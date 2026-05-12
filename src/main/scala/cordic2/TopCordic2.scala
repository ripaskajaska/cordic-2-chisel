package Cordic2
import chisel3._
import chisel3.util._

// Unified payload bundle used for both inputs and outputs of every pipeline stage
class Cordic2Payload(N: Int = 16) extends Bundle {
  val x = SInt(N.W)
  val y = SInt(N.W)
  val z = SInt(N.W)
}

// Cordic2Input as an alias
class Cordic2Input(N: Int = 16) extends Cordic2Payload(N)

class TopCordic2(N: Int = 16) extends Module {
  val input        = IO(Flipped(Valid(new Cordic2Payload(N))))
  val output       = IO(Valid(new Cordic2Payload(N)))

  val trivialRot   = Module(new TrivialRotations(N))
  val friendAngles = Module(new FriendAngles(N))
  val usrCordic    = Module(new USRCordic(N))
  val cordic0      = Module(new Cordic(N, 0))
  val cordic1      = Module(new Cordic(N, 1))
  val nanoRot      = Module(new NanoRotations(N))

  trivialRot.input   <> input
  friendAngles.input <> trivialRot.output
  usrCordic.input    <> friendAngles.output
  cordic0.input      <> usrCordic.output
  cordic1.input      <> cordic0.output
  nanoRot.input      <> cordic1.output
  output             <> nanoRot.output
}

