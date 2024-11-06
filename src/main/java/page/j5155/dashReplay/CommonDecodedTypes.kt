package page.j5155.dashReplay

import com.acmerobotics.roadrunner.Pose2d
import kotlinx.serialization.Serializable

@Serializable
data class Pose2dWithTime(var x: Double, var y: Double, var heading: Double, var timestamp: Long) {
    constructor(msg: Map<*,*>) :
        this(x= msg["x"] as Double,
        y=msg["y"] as Double,
        heading=msg["heading"] as Double,
        timestamp=msg["timestamp"] as Long)

    val pose: Pose2d
        get() {
            return Pose2d(x,y,heading)
        }

    operator fun compareTo(other: Pose2dWithTime): Int {
        return timestamp.compareTo(other.timestamp)
    }
}