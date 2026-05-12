package Cordic2
import chisel3._
import chisel3.util._

class USRCordic(N: Int = 16) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Payload(N))))
  val output = IO(Valid(new Cordic2Payload(N)))

  
}