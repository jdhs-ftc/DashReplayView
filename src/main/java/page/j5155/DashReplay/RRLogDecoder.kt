package page.j5155.DashReplay

import page.j5155.DashReplay.RRLogDecoder.EnumSchema
import page.j5155.DashReplay.RRLogDecoder.MessageSchema
import page.j5155.DashReplay.RRLogDecoder.PrimitiveSchema
import page.j5155.DashReplay.RRLogDecoder.StructSchema
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Dictionary
import java.util.Hashtable
import java.util.LinkedHashMap

class RRLogDecoder {
    var f: FileInputStream? = null
    var numberOfBytesRead: Int = 0

    interface MessageSchema
    class StructSchema(var fields: Map<String, MessageSchema>) : MessageSchema

    enum class PrimitiveSchema : MessageSchema {
        INT,
        LONG,
        DOUBLE,
        STRING,
        BOOLEAN
    }

    class EnumSchema(var constants: MutableList<String>) : MessageSchema


    @Throws(IOException::class)
    fun read(n: Int): ByteArray {
        numberOfBytesRead += n
        assert(n > 0)
        // assume this reads exactly n bytes or we reach EOF
        val buf = ByteArray(n)
        val readReturn = f!!.read(
            buf,
            0,
            n
        ) // This buffers
        if (readReturn == -1) {
            throw EOFException()
        }

        return buf
    }

    fun readString(): String {
        val nbytes = ByteBuffer.wrap((read(4))).getInt()
        return String(read(nbytes), StandardCharsets.UTF_8)
    }

    fun readSchema(): MessageSchema {
        val schemaType = ByteBuffer.wrap(read(4)).getInt()
        if (schemaType == 0) {
            val nfields = ByteBuffer.wrap(read(4)).getInt()
            val fields: MutableMap<String, MessageSchema> = LinkedHashMap<String, MessageSchema>()
            // TODO
            for (i in 0 until nfields) {
                val name = readString()
                fields.put(name, readSchema())
            }
            return StructSchema(fields)
            // primitive schema
        } else if (schemaType == 1) {
            return PrimitiveSchema.INT
        } else if (schemaType == 2) {
            return PrimitiveSchema.LONG
        } else if (schemaType == 3) {
            return PrimitiveSchema.DOUBLE
        } else if (schemaType == 4) {
            return PrimitiveSchema.STRING
        } else if (schemaType == 5) {
            return PrimitiveSchema.BOOLEAN
            // enum schema
        } else if (schemaType == 6) {
            val nconstants = ByteBuffer.wrap(read(4)).getInt()
            val constants: MutableList<String> = ArrayList<String>()
            for (i in 0 until nconstants) {
                constants.add(readString())
            }
            return EnumSchema(constants)
        } else {
            throw RuntimeException("Unknown schema type: $schemaType")
        }
    }

    fun readMsg(schema: MessageSchema): Any {
        if (schema is StructSchema) {
            val msg: Dictionary<String, Any> = Hashtable<String, Any>()
            for (entry in schema.fields.entries) {
                msg.put(entry.key, readMsg(entry.value))
            }
            return msg
        } else if (schema is PrimitiveSchema) {
            if (schema === PrimitiveSchema.INT) {
                return ByteBuffer.wrap(read(4)).getInt()
            } else if (schema === PrimitiveSchema.LONG) {
                return ByteBuffer.wrap(read(8)).getLong()
            } else if (schema === PrimitiveSchema.DOUBLE) {
                return ByteBuffer.wrap(read(8)).getDouble()
            } else if (schema === PrimitiveSchema.STRING) {
                return readString()
            } else if (schema === PrimitiveSchema.BOOLEAN) {
                return read(1)[0].toInt() == 1 // todo: this is not gonna work at all
            } else {
                throw RuntimeException("Unknown primitive schema: $schema")
            }
        } else if (schema is EnumSchema) {
            val ordinal = ByteBuffer.wrap(read(4)).getInt()
            return schema.constants[ordinal]
        } else {
            throw RuntimeException("Unknown schema: $schema")
        }
    }

    fun readFile(f: File): List<Map<String, *>> {
        this.f = FileInputStream(f)

        val magic = String(read(2), StandardCharsets.UTF_8)
        assert(magic == "RR") {
            "This is not a Roadrunner 1.0 log file" // error added by me j5155
        }
        val version = ByteBuffer.wrap(read(2)).getShort()
        assert(version.toInt() == 0) { "File version newer then expected (expected version 0 got version $version)" }

        val channels = ArrayList<String>()
        val schemas: MutableMap<String, MessageSchema> = LinkedHashMap<String, MessageSchema>()
        val messages: MutableMap<String, ArrayList<Any>> =
            LinkedHashMap<String, ArrayList<Any>>() // not sure whether these types are right

        while (true) {
            try {
                val entryType = ByteBuffer.wrap(read(4)).getInt()
                if (entryType == 0) {
                    // channel definition
                    val ch = readString()
                    schemas.put(ch, readSchema())
                    channels.add(ch)
                } else if (entryType == 1) {
                    // message
                    val chIndex = ByteBuffer.wrap(read(4)).getInt()
                    val ch = channels[chIndex]
                    messages.putIfAbsent(ch,ArrayList<Any>())
                    messages[ch]!!.add(readMsg(schemas[ch]!!)) // null type assertions here kinda strange
                } else {
                    throw RuntimeException("Unknown entry type: $entryType")
                }
            } catch (_: EOFException) {
                break
            }
        }
        return listOf<MutableMap<String, *>>(schemas, messages)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val d = RRLogDecoder()
            val file = File("/home/james/Documents/robotlogs/2024_02_16__19_52_54_834__FarParkLeftPixel.log")
            val fileContents = d.readFile(file)
            val schemas = fileContents[0] as MutableMap<String, MessageSchema>
            val messages = fileContents[1] as MutableMap<String, ArrayList<Any>>

            for (ch in schemas.keys) {
                val schema = schemas[ch]
                println("Channel: " + ch + " (" + messages[ch]!!.size + "messages)\n " + schema)
                for (o in messages[ch]!!) {
                    print(o.javaClass)
                    println(o)
                }
            }
        }
    }
}
