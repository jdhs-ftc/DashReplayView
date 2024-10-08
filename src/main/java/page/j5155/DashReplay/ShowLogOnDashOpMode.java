package page.j5155.DashReplay;

import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.Vector2d;
import page.j5155.DashReplay.testopmode.TestOpMode;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShowLogOnDashOpMode extends TestOpMode {
    TestDashboardInstance dashboard;
    ArrayList<Pose2d> targetPoses = new ArrayList<>();
    ArrayList<Pose2d> estPoses = new ArrayList<>();
    ArrayList<Long> targetTimestamps = new ArrayList<>();
    ArrayList<Long> estTimestamps = new ArrayList<>();
    long replayStartTime = System.nanoTime();
    long recordStartTime;

    public ShowLogOnDashOpMode() {
        super("ShowLogOnDashOpMode");
    }

    @Override
    protected void init() {
        dashboard = TestDashboardInstance.getInstance();

        RRLogDecoder d = new RRLogDecoder();
        File file = new File(System.getProperty("user.home") + "/Downloads/2024_10_05__21_57_33_666__LocalizationTest.log");
        List<Map<String,?>> fileContents = null;
        fileContents = d.readFile(file);
        Map<String, RRLogDecoder.MessageSchema> schemas = (Map<String, RRLogDecoder.MessageSchema>) fileContents.get(0);
        Map<String, ArrayList<Object>> messages = (Map<String, ArrayList<Object>>) fileContents.get(1);

        for (String ch : schemas.keySet()) {
            RRLogDecoder.MessageSchema schema = schemas.get(ch);
            System.out.println("Channel: " + ch + " (" + messages.get(ch).size() + "messages)\n " + schema);
            if (Objects.equals(ch, "ESTIMATED_POSE")) {
                for (Object msg: messages.get(ch)) {
                    Hashtable<String,Number> poseWithTime = (Hashtable<String,Number>) msg;
                    estPoses.add(new Pose2d((Double) poseWithTime.get("x"), // this casting is weird
                            (Double) poseWithTime.get("y"),
                            (Double) poseWithTime.get("heading")));
                    estTimestamps.add((Long) poseWithTime.get("timestamp"));
                }
            } else if (Objects.equals(ch, "TARGET_POSE")) {
                for (Object msg: messages.get(ch)) {
                    Hashtable<String,Number> poseWithTime = (Hashtable<String,Number>) msg;
                    targetPoses.add(new Pose2d((Double) poseWithTime.get("x"),
                            (Double) poseWithTime.get("y"),
                            (Double) poseWithTime.get("heading")));
                    targetTimestamps.add((Long) poseWithTime.get("timestamp"));
                }
            }
            if (messages.get(ch).size() < 3) {
                for (Object msg: messages.get(ch)) {
                    System.out.println(msg);
                }
            }

        }


        if (!estTimestamps.isEmpty()) {
            recordStartTime = estTimestamps.get(0);
        }
    }

    @Override
    protected void loop() throws InterruptedException {
        //draw the field
        TelemetryPacket packet = new TelemetryPacket(false);
        Canvas c = packet.fieldOverlay();
        c.setAlpha(0.4)
            .drawImage("https://raw.githubusercontent.com/acmerobotics/ftc-dashboard/master/FtcDashboard/dash/public/centerstage.webp", 0, 0, 144, 144)
                .setAlpha(1.0)
        .drawGrid(0, 0, 144, 144, 7, 7);

        // draw a line of all the target and estimated poses
        if (!targetPoses.isEmpty()) {
            c.setStroke("#4CAF50");
            c.setStrokeWidth(1);
            drawPoseList(c, targetPoses);
        }
        if (!estPoses.isEmpty()) {
            c.setStroke("#3F51B5");
            c.setStrokeWidth(1);
            drawPoseList(c, estPoses);
        }

        // find the current time, calculate the time since the replay started, and find where we are in the poses
        // then find which poses to show
        long offset = System.nanoTime() - replayStartTime;

        if (!estTimestamps.isEmpty() && recordStartTime + offset > estTimestamps.get(estTimestamps.size() - 1)) {
            // we're past the end of the recorded timestamps, so reset
            c.fillText("Replay over, looping...",30,50,"Arial",0);
            dashboard.sendTelemetryPacket(packet);
            Thread.sleep(1000);

            replayStartTime = System.nanoTime();
            offset = System.nanoTime() - replayStartTime;
        }
        long timeInReplay = recordStartTime + offset;
        if (!estTimestamps.isEmpty()) {
            long estTimestampToShow = estTimestamps.stream().min(Comparator.comparingLong(f -> Math.abs(f - timeInReplay))).orElse(estTimestamps.get(0)); // https://stackoverflow.com/questions/62559012/find-closest-object-in-java-collection-for-a-given-value-using-java8-stream
            Pose2d estPoseToShow = estPoses.get(estTimestamps.indexOf(estTimestampToShow)); // this seems inefficient

            c.setStroke("#3F51B5");
            drawRobot(c, estPoseToShow);
        }

        if (!targetTimestamps.isEmpty()) {
            long targetTimestampToShow = targetTimestamps.stream().min(Comparator.comparingLong(f -> Math.abs(f - timeInReplay))).orElse(targetTimestamps.get(0)); // https://stackoverflow.com/questions/62559012/find-closest-object-in-java-collection-for-a-given-value-using-java8-stream
            Pose2d targetPoseToShow = targetPoses.get(targetTimestamps.indexOf(targetTimestampToShow)); // this seems inefficient


            c.setStroke("#4CAF50");
            drawRobot(c, targetPoseToShow);
        }


        // send the data to dashboard
        dashboard.sendTelemetryPacket(packet);
        // busy wait
        Thread.sleep(10);
    }

    // written by rbrott, taken from drawing class in rr1.0 quickstart
    public static void drawRobot(Canvas c, Pose2d t) {
        final double ROBOT_RADIUS = 9;

        c.setStrokeWidth(1);
        c.strokeCircle(t.position.x, t.position.y, ROBOT_RADIUS);

        Vector2d halfv = t.heading.vec().times(0.5 * ROBOT_RADIUS);
        Vector2d p1 = t.position.plus(halfv);
        Vector2d p2 = p1.plus(halfv);
        c.strokeLine(p1.x, p1.y, p2.x, p2.y);
    }
    // also written by rbrott, taken from mecanumdrive class in rr1.0 quickstart
    private void drawPoseList(Canvas c, ArrayList<Pose2d> poses) {
        double[] xPoints = new double[poses.size()];
        double[] yPoints = new double[poses.size()];

        int i = 0;
        for (Pose2d t : poses) {
            xPoints[i] = t.position.x;
            yPoints[i] = t.position.y;

            i++;
        }

        c.strokePolyline(xPoints, yPoints);
    }
}
