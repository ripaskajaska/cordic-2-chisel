package Cordic2
import chisel3._
import chisel3.util._

class NanoRotations(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Input(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  
}