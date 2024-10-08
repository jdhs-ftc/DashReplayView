package page.j5155.dashReplay;

import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.Vector2d;
import page.j5155.dashReplay.testopmode.TestOpMode;
import page.j5155.dashReplay.RRLogDecoder.*;

import java.io.File;
import java.util.*;

public class ShowLogOnDashOpMode extends TestOpMode {
    TestDashboardInstance dashboard;
    ArrayList<Pose2dWithTime> targetPoses = new ArrayList<>();
    ArrayList<Pose2dWithTime> estPoses = new ArrayList<>();
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
        LogFile log = d.readFile(file);

        for (String chName : log.getChannels().keySet()) {
            Channel ch = log.getChannels().get(chName);
            System.out.println("Channel: " + chName + " (" + ch.getMessages().size() + "messages)\n " + ch.getSchema());
            if (ch.getMessages().size() < 3) {
                for (Object msg : ch.getMessages()) {
                    System.out.println(msg);
                }
            }
        }
        Channel estPosesCh = log.getChannels().get("ESTIMATED_POSE");
        if (estPosesCh != null) {
            for (Object msg : estPosesCh.getMessages()) {
                estPoses.add(new Pose2dWithTime(msg));
            }
        }
        Channel targetPosesCh = log.getChannels().get("TARGET_POSE");
        if (targetPosesCh != null) {
            for (Object msg : targetPosesCh.getMessages()) {
                targetPoses.add(new Pose2dWithTime(msg));
            }
        }

        if (!estPoses.isEmpty()) {
            recordStartTime = estPoses.get(0).getTimestamp();
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

        if (!estPoses.isEmpty() && recordStartTime + offset > estPoses.get(estPoses.size() - 1).getTimestamp()) {
            // we're past the end of the recorded timestamps, so reset
            c.fillText("Replay over, looping...", 30, 50, "Arial", 0);
            dashboard.sendTelemetryPacket(packet);
            Thread.sleep(1000);

            replayStartTime = System.nanoTime();
            offset = System.nanoTime() - replayStartTime;
        }
        long timeInReplay = recordStartTime + offset;
        if (!estPoses.isEmpty()) {
            Pose2dWithTime estPoseToShow = estPoses.stream().min(Comparator.comparingLong(f -> Math.abs(f.getTimestamp() - timeInReplay))).orElse(estPoses.get(0)); // https://stackoverflow.com/questions/62559012/find-closest-object-in-java-collection-for-a-given-value-using-java8-stream

            c.setStroke("#3F51B5");
            drawRobot(c, estPoseToShow.getPose());
        }

        if (!targetPoses.isEmpty()) {
            Pose2dWithTime targetPoseToShow = targetPoses.stream().min(Comparator.comparingLong(f -> Math.abs(f.getTimestamp() - timeInReplay))).orElse(targetPoses.get(0)); // https://stackoverflow.com/questions/62559012/find-closest-object-in-java-collection-for-a-given-value-using-java8-stream


            c.setStroke("#4CAF50");
            drawRobot(c, targetPoseToShow.getPose());
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
    // changed to Pose2dWithTime
    private void drawPoseList(Canvas c, ArrayList<Pose2dWithTime> poses) {
        double[] xPoints = new double[poses.size()];
        double[] yPoints = new double[poses.size()];

        int i = 0;
        for (Pose2dWithTime t : poses) {
            xPoints[i] = t.getX();
            yPoints[i] = t.getY();

            i++;
        }

        c.strokePolyline(xPoints, yPoints);
    }
}
