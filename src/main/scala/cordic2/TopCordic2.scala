package Cordic2
import chisel3._
import chisel3.util._

class Cordic2Payload(N: Int = 16) extends Bundle {
  val x = SInt(N.W)
  val y = SInt(N.W)
  val z = SInt(N.W)
}

class TopCordic2 extends Module {
  val input  = IO(Flipped(Valid(new Cordic2Input(N))))
  val output = IO(Valid(new Cordic2Payload(N)))
  
  val angleTransformer = Module(new AngleTransformer(N))
  val trivialRot   = Module(new TrivialRotations(N))
  val friendAngles = Module(new FriendAngles(N))
  val usrCordic    = Module(new USRCordic(N))
  val cordic0      = Module(new Cordic(N, 0))
  val cordic1      = Module(new Cordic(N, 1))
  val nanoRot      = Module(new NanoRotations(N))
}

class Cordic2Input(N: Int = 16) extends Bundle {
  val x = SInt(N.W)
  val y = SInt(N.W)
  val z = SInt(N.W)
}