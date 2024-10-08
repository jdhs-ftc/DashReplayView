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
import java.util.Arrays
import java.util.Dictionary
import java.util.Hashtable
import java.util.LinkedHashMap

class RRLogDecoder {
    var f: FileInputStream? = null
    var numberOfBytesRead: Int = 0

    interface MessageSchema
    class StructSchema(var fields: MutableMap<String?, MessageSchema?>) : MessageSchema

    enum class PrimitiveSchema : MessageSchema {
        INT,
        LONG,
        DOUBLE,
        STRING,
        BOOLEAN
    }

    class EnumSchema(var constants: MutableList<String?>) : MessageSchema


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

    @Throws(IOException::class)
    fun read_schema(): MessageSchema {
        val schema_type = ByteBuffer.wrap(read(4)).getInt()
        if (schema_type == 0) {
            val nfields = ByteBuffer.wrap(read(4)).getInt()
            val fields: MutableMap<String?, MessageSchema?> = LinkedHashMap<String?, MessageSchema?>()
            // TODO
            for (i in 0 until nfields) {
                val name = readString()
                fields.put(name, read_schema())
            }
            return StructSchema(fields)
            // primitive schema
        } else if (schema_type == 1) {
            return PrimitiveSchema.INT
        } else if (schema_type == 2) {
            return PrimitiveSchema.LONG
        } else if (schema_type == 3) {
            return PrimitiveSchema.DOUBLE
        } else if (schema_type == 4) {
            return PrimitiveSchema.STRING
        } else if (schema_type == 5) {
            return PrimitiveSchema.BOOLEAN
            // enum schema
        } else if (schema_type == 6) {
            val nconstants = ByteBuffer.wrap(read(4)).getInt()
            val constants: MutableList<String?> = ArrayList<String?>()
            for (i in 0 until nconstants) {
                constants.add(readString())
            }
            return EnumSchema(constants)
        } else {
            throw RuntimeException("Unknown schema type: " + schema_type)
        }
    }

    @Throws(IOException::class)
    fun read_msg(schema: MessageSchema?): Any? {
        if (schema is StructSchema) {
            val msg: Dictionary<String?, Any?> = Hashtable<String?, Any?>()
            // ending for the night, this corresponds to line 75 of the python code
            for (name in schema.fields.keys) {
                msg.put(name, read_msg(schema.fields.get(name))) // this casting is weird
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
                throw RuntimeException("Unknown primitive schema: " + schema)
            }
        } else if (schema is EnumSchema) {
            val ordinal = ByteBuffer.wrap(read(4)).getInt()
            return schema.constants.get(ordinal)
        } else {
            throw RuntimeException("Unknown schema: " + schema)
        }
    }

    @Throws(IOException::class)
    fun read_file(f: File): MutableList<MutableMap<String?, *>?> {
        this.f = FileInputStream(f)

        val magic = String(read(2), StandardCharsets.UTF_8)
        assert(magic == "RR") {
            "This is not a Roadrunner 1.0 log file" // error added by me j5155
        }
        val version = ByteBuffer.wrap(read(2)).getShort()
        assert(version.toInt() == 0) { "File version newer then expected (expected version 0 got version " + version + ")" }

        val channels = ArrayList<String?>()
        val schemas: MutableMap<String?, MessageSchema?> = LinkedHashMap<String?, MessageSchema?>()
        val messages: MutableMap<String?, ArrayList<Any?>?> =
            LinkedHashMap<String?, ArrayList<Any?>?>() // not sure whether these types are right

        while (true) {
            try {
                val entry_type = ByteBuffer.wrap(read(4)).getInt()
                if (entry_type == 0) {
                    // channel definition
                    val ch = readString()
                    schemas.put(ch, read_schema())
                    channels.add(ch)
                } else if (entry_type == 1) {
                    // message
                    val ch_index = ByteBuffer.wrap(read(4)).getInt()
                    val ch = channels.get(ch_index)
                    messages.computeIfAbsent(ch) { k: String? -> ArrayList<Any?>() }
                    messages.get(ch)!!.add(read_msg(schemas.get(ch)))
                } else {
                    throw RuntimeException("Unknown entry type: " + entry_type)
                }
            } catch (e: EOFException) {
                break
            }
        }
        return Arrays.asList<MutableMap<String?, *>?>(schemas, messages)
    }

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val d = RRLogDecoder()
            val file = File("/home/james/Documents/robotlogs/2024_02_16__19_52_54_834__FarParkLeftPixel.log")
            val fileContents = d.read_file(file)
            val schemas = fileContents.get(0) as MutableMap<String?, MessageSchema?>
            val messages = fileContents.get(1) as MutableMap<String?, ArrayList<Any>?>

            for (ch in schemas.keys) {
                val schema = schemas.get(ch)
                println("Channel: " + ch + " (" + messages.get(ch)!!.size + "messages)\n " + schema)
                for (o in messages.get(ch)!!) {
                    print(o.javaClass)
                    println(o)
                }
            }
        }
    }
}
