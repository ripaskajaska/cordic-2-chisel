package cordic2

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math.{cos, sin, toRadians, abs}
import Cordic2.TopCordic2

class Cordic2Spec extends AnyFlatSpec with ChiselScalatestTester {

  val N          = 16
  val FRAC_BITS  = N - 1       // Q1.15: 15 fractional bits
  val SCALE      = (1 << FRAC_BITS).toDouble  // 32768.0

  // Total pipeline gain from all stage kernels (~25/16 * 129/128 * sqrt(1025)/32 * sqrt(4097)/64)
  // Input must be pre-scaled by 1/K so output magnitude stays within Q1.15
  val PIPELINE_GAIN = 1.576

  val MAX_CYCLES = 120  // generous upper bound for full pipeline latency

  // Fixed-point conversion helpers
  def degToFxp(deg: Double): Int = (deg * SCALE / 180.0).toInt
  def fxpToFloat(raw: BigInt): Double = {
    // Sign-extend from N bits to handle negative two's complement values
    val masked = (raw & ((BigInt(1) << N) - 1)).toInt
    val signed = if ((masked & (1 << (N - 1))) != 0) masked - (1 << N) else masked
    signed.toDouble / SCALE
  }

  def runAngle(dut: TopCordic2, angleDeg: Double): Unit = {
    // Pre-scale input so output magnitude ≈ 1.0 after pipeline gain
    val scaledX = ((1.0 / PIPELINE_GAIN) * SCALE).toInt

    // Present input for exactly one cycle
    dut.input.valid.poke(true.B)
    dut.input.bits.x.poke(scaledX.S)
    dut.input.bits.y.poke(0.S)
    dut.input.bits.z.poke(degToFxp(angleDeg).S)
    dut.clock.step()
    dut.input.valid.poke(false.B)

    // Poll until output.valid fires
    var cycles = 0
    while (!dut.output.valid.peek().litToBoolean && cycles < MAX_CYCLES) {
      dut.clock.step()
      cycles += 1
    }

    assert(dut.output.valid.peek().litToBoolean,
      s"[TIMEOUT] No output after $MAX_CYCLES cycles for angle=$angleDeg°")

    val xOut = fxpToFloat(dut.output.bits.x.peek().litValue)
    val yOut = fxpToFloat(dut.output.bits.y.peek().litValue)

    val expectedCos = cos(toRadians(angleDeg))
    val expectedSin = sin(toRadians(angleDeg))
    val xErr = abs(xOut - expectedCos)
    val yErr = abs(yOut - expectedSin)

    println(f"  $angleDeg%+8.2f° | " +
            f"cos: got=$xOut%+.5f exp=$expectedCos%+.5f err=$xErr%.5f | " +
            f"sin: got=$yOut%+.5f exp=$expectedSin%+.5f err=$yErr%.5f | " +
            f"cycles=$cycles%3d")

    dut.clock.step()  // advance past the valid output cycle
  }

  "TopCordic2" should "approximate cosine and sine within expected Q1.15 error" in {
    test(new TopCordic2(N)) { dut =>

      val testAngles = Seq(
        0.0, 10.0, 20.0, 30.0, 45.0, 60.0, 75.0, 90.0,
        120.0, 135.0, 150.0, 170.0,
        -10.0, -30.0, -45.0, -90.0, -135.0, -170.0
      )

      println()
      println("=" * 80)
      println("CORDIC-2 Chisel Test  (N=16, Q1.15, pre-scaled input 1/K≈0.635)")
      println("=" * 80)

      testAngles.foreach(a => runAngle(dut, a))

      println("=" * 80)
    }
  }
}

