package ink.abb.pogo.api


import com.sun.codemodel.*
import org.gradle.internal.impldep.org.apache.commons.io.output.NullOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.util.*

@Throws(ClassNotFoundException::class, IOException::class)
private fun getClasses(packageName: String): Array<Class<*>> {
    val classLoader = Thread.currentThread().contextClassLoader!!
    val path = packageName.replace('.', '/')
    val resources = classLoader.getResources(path)
    val dirs = ArrayList<File>()
    while (resources.hasMoreElements()) {
        val resource = resources.nextElement()
        dirs.add(File(resource.file))
    }
    val classes = ArrayList<Class<*>>()
    for (directory in dirs) {
        classes.addAll(findClasses(directory, packageName))
    }
    return classes.toArray(arrayOfNulls<Class<*>>(classes.size))
}

/**
 * Recursive method used to find all classes in a given directory and subdirs.

 * @param directory   The base directory
 * *
 * @param packageName The package name for classes found inside the base directory
 * *
 * @return The classes
 * *
 * @throws ClassNotFoundException
 */
@Throws(ClassNotFoundException::class)
private fun findClasses(directory: File, packageName: String): List<Class<*>> {
    val classes = ArrayList<Class<*>>()
    if (!directory.exists()) {
        return classes
    }
    val files = directory.listFiles()
    for (file in files) {
        if (file.isDirectory) {
            assert(!file.name.contains("."))
            classes.addAll(findClasses(file, packageName + "." + file.name))
        } else if (file.name.endsWith(".class")) {
            classes.add(Class.forName(packageName + '.' + file.name.substring(0, file.name.length - 6)))
        }
    }
    return classes
}

fun String.camelToUnderscores() = "[A-Z\\d]".toRegex().replace(this, {
    "_" + it.groups[0]!!.value.toLowerCase()
})

fun main(a: Array<String>) {
    val codeModel = JCodeModel()
    val fqcn = "ink.abb.pogo.api.request"

    val classes = getClasses("POGOProtos.Networking.Requests.Messages")

    classes.filter { it.simpleName.endsWith("Message") }
            .forEach {
                val messageClass = it
                val className = messageClass.simpleName.removeSuffix("Message")

                val clazz = codeModel._class(JMod.PUBLIC, fqcn + "." + className, ClassType.CLASS)._implements(codeModel.ref("ink.abb.pogo.api.network.ServerRequest"))
                val builderClass = it.classes.find {
                    it.name == "${messageClass.name}\$Builder"
                }!!
                val builderField = clazz.field(JMod.PRIVATE, builderClass, "builder")

                val methodWithInitialization = clazz.constructor(JMod.PUBLIC)
                val bodyWithInitialization = methodWithInitialization.body()

                val constructorWithBuilder = clazz.constructor(JMod.PRIVATE)
                //val constructor = clazz.constructor(JMod.PRIVATE)

                var builder = codeModel.ref(messageClass).staticInvoke("newBuilder")

                val needApiLocation = mutableSetOf<String>()

                var createSetters = false

                builderClass.methods
                        .filter {
                            it.modifiers and JMod.STATIC == 0 &&
                                    it.returnType == builderClass &&
                                    ((it.name.startsWith("set") && it.parameterCount == 1) ||
                                            (it.name.startsWith("add") && it.parameterCount == 1)) &&
                                    !it.name.endsWith("Bytes") &&
                                    it.parameters[0].type.simpleName != "Builder" &&
                                    !it.name.startsWith("addAll")
                        }
                        .forEach {
                            var name = it.name.removePrefix("set")
                            name = name[0].toLowerCase() + name.substring(1)

                            if (!it.name.startsWith("add")) {
                                if ((name.contains("altitude", ignoreCase = true) || name.contains("longitude", ignoreCase = true) || name.contains("latitude", ignoreCase = true)) && !name.contains("fort", ignoreCase = true) && !name.contains("gym", ignoreCase = true)) {
                                    needApiLocation.add(it.name)
                                } else {
                                    createSetters = true
                                    val param = methodWithInitialization.param(it.parameters[0].type, name)
                                    builder = builder.invoke(it.name).arg(param)
                                }
                            }
                            val oldName = name
                            val type: Class<*> = it.parameters[0].type
                            if (it.name.startsWith("add")) {
                                val withName = it.name.removePrefix("add")
                                name = "with${withName.substring(0, withName.length)}"
                            } else {
                                name = "with${it.name.removePrefix("set")}"
                            }
                            val setter = clazz.method(JMod.PUBLIC, clazz, name)
                            val param = setter.param(type, oldName)
                            setter.body().invoke(builderField, it.name).arg(param)
                            setter.body()._return(JExpr._this())
                        }


                if (createSetters) {
                    val constructor = clazz.constructor(JMod.PUBLIC)
                    val body = constructor.body()
                    body.assign(JExpr._this().ref(builderField), codeModel.ref(messageClass).staticInvoke("newBuilder"))
                }

                val builderParam = constructorWithBuilder.param(builderClass, "builder")
                constructorWithBuilder.body().assign(JExpr._this().ref(builderField), builderParam)

                //constructor.body().assign(JExpr._this().ref(builderField), codeModel.ref(messageClass).staticInvoke("newBuilder"))

                // TODO of all the ugly things in this code, this is the worst
                bodyWithInitialization.invoke("this").arg(builder)

                val responseType = codeModel.ref("POGOProtos.Networking.Responses.${className}ResponseOuterClass.${className}Response")
                val responseField = clazz.field(JMod.PRIVATE, responseType, "response")
                val setResponse = clazz.method(JMod.PUBLIC, codeModel.ref("void"), "setResponse")
                val setParam = setResponse.param(codeModel.ref("com.google.protobuf.ByteString"), "response")
                val setBody = setResponse.body()
                val tryBlock = setBody._try()
                tryBlock.body().assign(JExpr._this().ref(responseField), (responseType.staticInvoke("parseFrom").arg(setParam)))

                tryBlock._catch(codeModel.ref(Exception::class.java))
                //setBody.assign(JExpr._this().ref(responseField), setParam)

                val getResponse = clazz.method(JMod.PUBLIC, responseType, "getResponse")
                getResponse.body()._return(responseField)

                val buildMethod = clazz.method(JMod.PUBLIC, com.google.protobuf.GeneratedMessage::class.java, "build")
                val apiParam = buildMethod.param(codeModel.ref("ink.abb.pogo.api.PoGoApi"), "poGoApi")

                val getRequestType = clazz.method(JMod.PUBLIC, codeModel.ref("POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType"), "getRequestType")
                getRequestType.body()._return(codeModel.ref("POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType").staticRef(className.camelToUnderscores().toUpperCase().substring(1)))


                needApiLocation.forEach {
                    val isLat = it.contains("lat", ignoreCase = true)

                    buildMethod.body().add(builderField.invoke(it).arg(if (isLat) {
                        apiParam.invoke("getLatitude")
                    } else {
                        apiParam.invoke("getLongitude")
                    }))
                }
                buildMethod.body()._return(builderField.invoke("build"))
            }

    //println(a[0])


    val folder = File(a[0])

// If it doesn't exist
    if (!folder.exists()) {
        // Create all folders up-to and including B
        folder.mkdirs()
    }
    codeModel.build(folder, PrintStream(NullOutputStream()))
}