package page.j5155.dashReplay

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import page.j5155.dashReplay.RRLogDecoder.EnumSchema
import page.j5155.dashReplay.RRLogDecoder.MessageSchema
import page.j5155.dashReplay.RRLogDecoder.PrimitiveSchema
import page.j5155.dashReplay.RRLogDecoder.StructSchema
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Dictionary
import java.util.Hashtable
import java.util.LinkedHashMap

class RRLogDecoder {
    lateinit var f: InputStream
    var numberOfBytesRead: Int = 0

    interface MessageSchema
    class StructSchema(val fields: Map<String, MessageSchema>) : MessageSchema {
        override fun toString(): String {
            return "StructSchema(fields=$fields)"
        }
    }

    enum class PrimitiveSchema : MessageSchema {
        INT,
        LONG,
        DOUBLE,
        STRING,
        BOOLEAN
    }

    class EnumSchema(var constants: MutableList<String>) : MessageSchema {
        override fun toString(): String {
            return "EnumSchema(constants=$constants)"
        }
    }
    //@Serializable
    data class LogFile(val channels: LinkedHashMap<String, Channel>)

    //@Serializable
    data class Channel(val schema: MessageSchema, @Contextual var messages: ArrayList<Any> = ArrayList()) // messages a var
    // so that other stuff can shorten it

    fun read(n: Int): ByteArray {
        numberOfBytesRead += n
        assert(n > 0)
        // assume this reads exactly n bytes or we reach EOF
        val buf = ByteArray(n)
        val readReturn = f.read(
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
            val fields = LinkedHashMap<String, MessageSchema>()
            repeat(nfields) {
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
            repeat (nconstants) {
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
            return if (schema == PrimitiveSchema.INT) {
                ByteBuffer.wrap(read(4)).getInt()
            } else if (schema == PrimitiveSchema.LONG) {
                ByteBuffer.wrap(read(8)).getLong()
            } else if (schema == PrimitiveSchema.DOUBLE) {
                ByteBuffer.wrap(read(8)).getDouble()
            } else if (schema == PrimitiveSchema.STRING) {
                readString()
            } else if (schema == PrimitiveSchema.BOOLEAN) {
                read(1)[0].toInt() == 1
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

    fun readFile(f: File): LogFile {
        return readInputStream(FileInputStream(f))
    }
    fun readByteArray(f: ByteArray): LogFile {
        return readInputStream(ByteArrayInputStream(f))
    }
    fun readInputStream(f: InputStream): LogFile {
        this.f = f

        val magic = String(read(2), StandardCharsets.UTF_8)
        assert(magic == "RR") { "This is not a Road Runner 1.0 log file" }
        val version = ByteBuffer.wrap(read(2)).getShort()
        assert(version.toInt() == 0) { "File version newer then expected (expected version 0 got version $version)" }

        val channels = LinkedHashMap<String,Channel>()

        while (true) {
            try {
                val entryType = ByteBuffer.wrap(read(4)).getInt()
                if (entryType == 0) {
                    // channel definition
                    val ch = readString()
                    val schema = readSchema()
                    channels.put(ch,Channel(schema))
                } else if (entryType == 1) {
                    // message
                    val chIndex = ByteBuffer.wrap(read(4)).getInt()
                    val ch: Channel = channels.values.elementAt(chIndex)
                    val msg = readMsg(ch.schema)
                    ch.messages.add(msg) // pass by reference?? I hope???
                } else {
                    throw RuntimeException("Unknown entry type: $entryType at $numberOfBytesRead - 4 bytes")
                }
            } catch (_: EOFException) {
                break
            }
        }
        return LogFile(channels)
    }

}

        fun main() {
            val d = RRLogDecoder()
            val file = File(System.getProperty("user.home") + "/Downloads/2025_03_15__03_43_35_505__SplineTest.log.json")
            val logFile = d.readFile(file)

            for (ch in logFile.channels) { // for each channel
                println("Channel: " + ch.key + " (" + ch.value + "messages)\n " + ch.value.schema)
                for (o in ch.value.messages) {
                    print(o.javaClass)
                    println(o)
                }
            }
        }
