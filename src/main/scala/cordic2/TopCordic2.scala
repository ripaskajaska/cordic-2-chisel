import chisel3._
import chisel3.util._

class Cordic2Payload extends Bundle {
  val x = SInt(16.W)
  val y = SInt(16.W)
  val z = UInt(16.W)
}

class TopCordic2 extends Module {
    val input  = IO(Flipped(Decoupled(new Cordic2Payload)))
    val output = IO(Decoupled(new Cordic2Payload))
    
}