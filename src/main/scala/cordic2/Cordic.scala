import chisel3._
import chisel3.util._
import cordic2._

class Cordic(N: Int = 16, i: Int = 0) extends Module {
  val input = IO(Flipped(Valid(new Cordic2Input(N))))
  val output = IO(Valid(new Cordic2Payload(N)))


}