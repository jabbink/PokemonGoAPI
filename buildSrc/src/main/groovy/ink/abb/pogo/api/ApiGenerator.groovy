package ink.abb.pogo.api

import com.google.protobuf.GeneratedMessage
import com.sun.codemodel.ClassType
import com.sun.codemodel.JCodeModel
import com.sun.codemodel.JExpr
import com.sun.codemodel.JMod
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by maxke on 13.08.2016.
 * Port of the original API generation code to groovy
 */
class ApiGenerator implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def targetDir = new File("${project.buildDir}/generated/source/java/main")

        project.task("generateApi") << {
            generateApi(targetDir)
        }

        project.tasks.findAll {it.name.startsWith("compile")}*.dependsOn project.tasks["generateApi"]
        project.sourceSets.main.java.srcDirs += targetDir.absolutePath
        project.sourceSets.main.java.srcDirs += "${project.buildDir}/generated/source/proto/main/java"
    }

    private static ArrayList<Class> getClasses(String packageName) {
        def classLoader = Thread.currentThread().contextClassLoader
        def path = packageName.replace('.', '/')
        def resources = classLoader.getResources(path)
        def dirs = new ArrayList<File>()
        while (resources.hasMoreElements()) {
            def resource = resources.nextElement()
            dirs.add(new File(resource.file))
        }
        def classes = []
        for (directory in dirs) {
            classes.addAll(findClasses(directory, packageName))
        }
        return classes
    }

    private static def findClasses(File directory, String packageName) {
        def classes = []
        if (!directory.exists()) {
            return classes
        }
        def files = directory.listFiles()
        for (file in files) {
            if (file.directory) {
                assert (!file.name.contains("."))
                classes.addAll(findClasses(file, packageName + "." + file.name))
            } else if (file.name.endsWith(".class")) {
                classes.add(Class.forName(packageName + '.' + file.name.substring(0, file.name.length() - 6)))
            }
        }
        return classes
    }

    private static def camelToUnderscores(String input) {
        input.replaceAll(/\B[A-Z]/) { '_' + it }.toLowerCase()
    }

    static def generateApi(File outputDir) {
        def codeModel = new JCodeModel()
        def fqcn = "ink.abb.pogo.api.request"

        def classes = getClasses("POGOProtos.Networking.Requests.Messages")
        classes.findAll({ it.simpleName.endsWith("Message") })
                .forEach {
            def messageClass = it
            def className = messageClass.simpleName - "Message"

            def clazz = codeModel._class(JMod.PUBLIC, fqcn + "." + className, ClassType.CLASS)._implements(codeModel.ref("ink.abb.pogo.api.network.ServerRequest"))
            def builderClass = it.classes.find {
                it.name == "${messageClass.name}\$Builder".toString()
            }

            def builderField = clazz.field(JMod.PRIVATE, builderClass, "builder")

            def methodWithInitialization = clazz.constructor(JMod.PUBLIC)
            def bodyWithInitialization = methodWithInitialization.body()

            def constructorWithBuilder = clazz.constructor(JMod.PRIVATE)
            //def constructor = clazz.constructor(JMod.PRIVATE)

            def builder = codeModel.ref(messageClass).staticInvoke("newBuilder")

            def needApiLocation = new HashSet<String>()

            def createSetters = false

            builderClass.methods.findAll {
                (it.modifiers & JMod.STATIC) == 0 &&
                        it.returnType == builderClass &&
                        ((it.name.startsWith("set") && it.parameterCount == 1) ||
                                (it.name.startsWith("add") && it.parameterCount == 1)) &&
                        !it.name.endsWith("Bytes") &&
                        it.parameters[0].type.simpleName != "Builder" &&
                        !it.name.startsWith("addAll")
            }.forEach {
                def name = it.name - "set"
                name = name[0].toLowerCase() + name.substring(1)

                if (!it.name.startsWith("add")) {
                    def nameLower = name.toLowerCase()
                    if (!messageClass.simpleName.contains("FortDetails") && (nameLower.contains("altitude") || nameLower.contains("longitude") || nameLower.contains("latitude")) && !nameLower.contains("fort") && !nameLower.contains("gym")) {
                        needApiLocation.add(it.name)
                    } else {
                        createSetters = true
                        def param = methodWithInitialization.param(it.parameters[0].type, name)
                        builder = builder.invoke(it.name).arg(param)
                    }
                }
                def oldName = name
                def type = it.parameters[0].type
                if (it.name.startsWith("add")) {
                    def withName = it.name - "add"
                    name = "with${withName.substring(0, withName.length())}"
                } else {
                    name = "with${it.name - "set"}"
                }
                def setter = clazz.method(JMod.PUBLIC, clazz, name)
                def param = setter.param(type, oldName)
                setter.body().invoke(builderField, it.name).arg(param)
                setter.body()._return(JExpr._this())
            }


            if (createSetters) {
                def constructor = clazz.constructor(JMod.PUBLIC)
                def body = constructor.body()
                body.assign(JExpr._this().ref(builderField), codeModel.ref(messageClass).staticInvoke("newBuilder"))
            }

            def builderParam = constructorWithBuilder.param(builderClass, "builder")
            constructorWithBuilder.body().assign(JExpr._this().ref(builderField), builderParam)

            //constructor.body().assign(JExpr._this().ref(builderField), codeModel.ref(messageClass).staticInvoke("newBuilder"))

            // TODO of all the ugly things in this code, this is the worst
            bodyWithInitialization.invoke("this").arg(builder)

            def responseType = codeModel.ref("POGOProtos.Networking.Responses.${className}ResponseOuterClass.${className}Response")
            def responseField = clazz.field(JMod.PRIVATE, responseType, "response")
            def setResponse = clazz.method(JMod.PUBLIC, codeModel.ref("void"), "setResponse")
            def setParam = setResponse.param(codeModel.ref("com.google.protobuf.ByteString"), "response")
            def setBody = setResponse.body()
            def tryBlock = setBody._try()
            tryBlock.body().assign(JExpr._this().ref(responseField), (responseType.staticInvoke("parseFrom").arg(setParam)))

            tryBlock._catch(codeModel.ref(Exception))
            //setBody.assign(JExpr._this().ref(responseField), setParam)

            def getResponse = clazz.method(JMod.PUBLIC, responseType, "getResponse")
            getResponse.body()._return(responseField)

            def buildMethod = clazz.method(JMod.PUBLIC, GeneratedMessage, "build")
            def apiParam = buildMethod.param(codeModel.ref("ink.abb.pogo.api.PoGoApi"), "poGoApi")

            def getRequestType = clazz.method(JMod.PUBLIC, codeModel.ref("POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType"), "getRequestType")
            getRequestType.body()._return(codeModel.ref("POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType").staticRef(camelToUnderscores(className).toUpperCase()))

            def getBuilder = clazz.method(JMod.PUBLIC, builderClass, "getBuilder")
            getBuilder.body()._return(builderField)

            needApiLocation.forEach {
                def isLat = it.toLowerCase().contains("lat")
                buildMethod.body().add(builderField.invoke(it).arg(isLat ? apiParam.invoke("getLatitude") : apiParam.invoke("getLongitude")))
            }
            buildMethod.body()._return(builderField.invoke("build"))
        }

        // If it doesn't exist
        if (!outputDir.exists()) {
            // Create all folders up-to and including B
            outputDir.mkdirs()
        }

        codeModel.build(outputDir, null as PrintStream)
    }

}
