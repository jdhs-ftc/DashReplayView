package page.j5155.DashReplay;

import com.acmerobotics.dashboard.DashboardCore;
import com.acmerobotics.dashboard.RobotStatus;
import com.acmerobotics.dashboard.SendFun;
import com.acmerobotics.dashboard.SocketHandler;
import com.acmerobotics.dashboard.message.Message;
import com.acmerobotics.dashboard.message.redux.InitOpMode;
import com.acmerobotics.dashboard.message.redux.ReceiveOpModeList;
import com.acmerobotics.dashboard.message.redux.ReceiveRobotStatus;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.stream.Collectors;


public class Playback {
    private static final Playback instance = new Playback();
    private static final String replayFilePath = System.getProperty("user.home") + "/Documents/dashboardreplay.txt";
    static final long startTimeMs = 1720987470319L;
    long actualStartTime = System.currentTimeMillis();
    long timeOffset = actualStartTime - startTimeMs;
    long lastSentMsgTime = 0;

    final String DEFAULT_OP_MODE_NAME = "$Stop$Robot$";

    private TelemetryPacket currentPacket;

    DashboardCore core = new DashboardCore(false);

    private NanoWSD server = new NanoWSD(8000) {
        @Override
        protected WebSocket openWebSocket(IHTTPSession handshake) {
            return new DashWebSocket(handshake);
        }
    };

    private class DashWebSocket extends NanoWSD.WebSocket implements SendFun {
        final SocketHandler sh = core.newSocket(this);

        public DashWebSocket(NanoHTTPD.IHTTPSession handshakeRequest) {
            super(handshakeRequest);
        }

        @Override
        public void send(Message message) {
            try {
                String messageStr = DashboardCore.GSON.toJson(message);
                send(messageStr);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void onOpen() {
            sh.onOpen();
        }

        @Override
        protected void onClose(NanoWSD.WebSocketFrame.CloseCode code, String reason,
                               boolean initiatedByRemote) {
            sh.onClose();
        }

        @Override
        protected void onMessage(NanoWSD.WebSocketFrame message) {
            String payload = message.getTextPayload();
            Message msg = DashboardCore.GSON.fromJson(payload, Message.class);

            if (sh.onMessage(msg)) {
                return;
            }

            switch (msg.getType()) {
                case GET_ROBOT_STATUS: {
                    String opModeName;
                    RobotStatus.OpModeStatus opModeStatus;
                    opModeName = DEFAULT_OP_MODE_NAME;
                    opModeStatus = RobotStatus.OpModeStatus.STOPPED;

                    send(new ReceiveRobotStatus(
                        new RobotStatus(core.enabled, true, opModeName, opModeStatus, "", "", 12087.0)
                    ));
                    break;
                }
                default:
                    System.out.println(msg.getType());
            }
        }

        @Override
        protected void onPong(NanoWSD.WebSocketFrame pong) {

        }

        @Override
        protected void onException(IOException exception) {

        }
    }

    public static Playback getInstance() {
        return instance;
    }

    public void start() throws InterruptedException {
        System.out.println("Starting Dashboard instance");

        core.enabled = true;
        core.replayEnabled = false;
        core.replayFilePath = System.getProperty("user.home") + "/Documents/replayer.txt";

        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(replayFilePath));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    actualStartTime = System.currentTimeMillis();
                    timeOffset = actualStartTime - startTimeMs;
                    lastSentMsgTime = 0;
                    reader = new BufferedReader(new FileReader(replayFilePath));
                    continue;
                }
                long timeMsgSent;
                try {
                    timeMsgSent = Long.parseLong(line);
                } catch (NumberFormatException e) {
                    continue;
                }
                long timeToSend = System.currentTimeMillis() - timeOffset;
                if (timeMsgSent < timeToSend || timeMsgSent < lastSentMsgTime) {
                    continue;
                }
                Thread.sleep(timeMsgSent - timeToSend);
                //core.sendAll(DashboardCore.GSON.fromJson(reader.readLine(), Message.class));
                BufferedReader finalReader = reader;
                core.sockets.with(l -> {
                    for (SendFun sf : l) {
                        try {
                            ((NanoWSD.WebSocket) sf).send(finalReader.readLine());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                lastSentMsgTime = timeMsgSent;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Thread.yield();
        }
    }

    public void addData(String x, Object o) {
        if (currentPacket == null) {
            currentPacket = new TelemetryPacket();
        }

        currentPacket.put(x, o);
    }

    public void update() {
        if (currentPacket != null) {
            core.sendTelemetryPacket(currentPacket);
            currentPacket = null;
        }
    }

    public void sendTelemetryPacket(TelemetryPacket t) {
        core.sendTelemetryPacket(t);
    }
}
