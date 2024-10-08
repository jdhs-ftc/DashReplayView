package page.j5155.DashReplay;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RRLogDecoder {
    FileInputStream f;
    int numberOfBytesRead = 0;

    interface MessageSchema {}
    static class StructSchema implements MessageSchema {
        Map<String,MessageSchema> fields;
        StructSchema(Map<String,MessageSchema> fields) {
            this.fields = fields;
        }
    }
    enum PrimitiveSchema implements MessageSchema {
        INT,
        LONG,
        DOUBLE,
        STRING,
        BOOLEAN
    }

    static class EnumSchema implements MessageSchema {
        List<String> constants;

        public EnumSchema(List<String> constants) {
            this.constants = constants;
        }
    }


    byte[] read(int n) throws IOException {
        numberOfBytesRead += n;
        assert n > 0;
        // assume this reads exactly n bytes or we reach EOF
        byte[] buf = new byte[n];
        int readReturn = f.read(buf, 0, n); // Does this actually buffer? Or does this need a different offset every time? TODO: find out
        if (readReturn == -1) {
            throw new EOFException();
        }

        return buf;
    }
    String read_string() throws IOException {
        int nbytes = ByteBuffer.wrap((read(4))).getInt();
        return new String(read(nbytes), StandardCharsets.UTF_8);
    }

    MessageSchema read_schema() throws IOException {
        int schema_type = ByteBuffer.wrap(read(4)).getInt();
        if (schema_type == 0) {
            int nfields = ByteBuffer.wrap(read(4)).getInt();
            Map<String,MessageSchema> fields = new LinkedHashMap<>();
            // TODO
            for (int i = 0; i < nfields; i++) {
                String name = read_string();
                fields.put(name, read_schema());
            }
            return new StructSchema(fields);
        // primitive schema
        } else if (schema_type == 1) {
            return PrimitiveSchema.INT;
        } else if (schema_type == 2) {
            return PrimitiveSchema.LONG;
        } else if (schema_type == 3) {
            return PrimitiveSchema.DOUBLE;
        } else if (schema_type == 4) {
            return PrimitiveSchema.STRING;
        } else if (schema_type == 5) {
            return PrimitiveSchema.BOOLEAN;
        // enum schema
        } else if (schema_type == 6) {
            int nconstants = ByteBuffer.wrap(read(4)).getInt();
            List<String> constants = new ArrayList<>();
            for (int i = 0; i < nconstants; i++) {
                constants.add(read_string());
            }
            return new EnumSchema(constants);
        } else {
            throw new RuntimeException("Unknown schema type: " + schema_type);
        }

    }
    Object read_msg(MessageSchema schema) throws IOException {
        if (schema instanceof StructSchema) {
            Dictionary<String,Object> msg = new Hashtable<>();
            // ending for the night, this corresponds to line 75 of the python code
            for (String name : ((StructSchema) schema).fields.keySet()) {
                msg.put(name, read_msg(((StructSchema) schema).fields.get(name))); // this casting is weird
            }
            return msg;
        } else if (schema instanceof PrimitiveSchema) {
            if (schema == PrimitiveSchema.INT) {
                return ByteBuffer.wrap(read(4)).getInt();
            } else if (schema == PrimitiveSchema.LONG) {
                return ByteBuffer.wrap(read(8)).getLong();
            } else if (schema == PrimitiveSchema.DOUBLE) {
                return ByteBuffer.wrap(read(8)).getDouble();
            } else if (schema == PrimitiveSchema.STRING) {
                return read_string();
            } else if (schema == PrimitiveSchema.BOOLEAN) {
                return read(1)[0] == 1; // todo: this is not gonna work at all
            } else {
                throw new RuntimeException("Unknown primitive schema: " + schema);
            }
        } else if (schema instanceof EnumSchema) {
            int ordinal = ByteBuffer.wrap(read(4)).getInt();
            return ((EnumSchema) schema).constants.get(ordinal);
        } else {
            throw new RuntimeException("Unknown schema: " + schema);
        }
    }

    List<Map<String, ?>> read_file(File f) throws IOException {
        this.f = new FileInputStream(f);

        String magic = new String(read(2),StandardCharsets.UTF_8);
        assert magic.equals("RR") : "This is not a Roadrunner 1.0 log file"; // error added by me j5155
        short version = ByteBuffer.wrap(read(2)).getShort();
        assert version == 0 : "File version newer then expected (expected version 0 got version " + version + ")";

        ArrayList<String> channels = new ArrayList<>();
        Map<String,MessageSchema> schemas = new LinkedHashMap<>();
        Map<String,ArrayList<Object>> messages = new LinkedHashMap<>(); // not sure whether these types are right

        while (true) {
            try {
                int entry_type = ByteBuffer.wrap(read(4)).getInt();
                if (entry_type == 0) {
                    // channel definition
                    String ch = read_string();
                    schemas.put(ch, read_schema());
                    channels.add(ch);
                } else if (entry_type == 1) {
                    // message
                    int ch_index = ByteBuffer.wrap(read(4)).getInt();
                    String ch = channels.get(ch_index);
                    messages.computeIfAbsent(ch, k -> new ArrayList<>());
                    messages.get(ch).add(read_msg(schemas.get(ch)));
                } else {
                    throw new RuntimeException("Unknown entry type: " + entry_type);
                }
            } catch (EOFException e) {
                break;
            }
        }
        return Arrays.asList(schemas,messages);

    }

    public static void main(String[] args) throws IOException {
        RRLogDecoder d = new RRLogDecoder();
        File file = new File("/home/james/Documents/robotlogs/2024_02_16__19_52_54_834__FarParkLeftPixel.log");
        List<Map<String,?>> fileContents = d.read_file(file);
        Map<String,MessageSchema> schemas = (Map<String, MessageSchema>) fileContents.get(0);
        Map<String,ArrayList<Object>> messages = (Map<String, ArrayList<Object>>) fileContents.get(1);

        for (String ch : schemas.keySet()) {
            MessageSchema schema = schemas.get(ch);
            System.out.println("Channel: " + ch + " (" + messages.get(ch).size() + "messages)\n " + schema);
            for (Object o : messages.get(ch)) {
                System.out.print(o.getClass());
                System.out.println(o);
            }

        }
    }

}
