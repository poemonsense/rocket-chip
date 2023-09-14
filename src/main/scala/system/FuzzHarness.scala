package freechips.rocketchip.system


import chisel3._
import chisel3.util.Queue
import freechips.rocketchip.regmapper.LFSR16Seed
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage._
import difftest.{DifftestModule, LogCtrlIO, PerfInfoIO, UARTIO}
import firrtl.options.Shell
import freechips.rocketchip.devices.debug.{Debug, DebugModuleKey}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.AsyncResetReg
import org.chipsalliance.cde.config.{Config, Parameters}

class ExampleFuzzSystem(implicit p: Parameters) extends RocketSubsystem
  with CanHaveMasterAXI4MemPort
{
  // optionally add ROM devices
  // Note that setting BootROMLocated will override the reset_vector for all tiles
  val bootROM  = p(BootROMLocated(location)).map { BootROM.attach(_, this, CBUS) }
  val maskROMs = p(MaskROMLocated(location)).map { MaskROM.attach(_, this, CBUS) }

  override lazy val module = new ExampleFuzzSystemImp(this)
}

class ExampleFuzzSystemImp[+L <: ExampleFuzzSystem](_outer: L) extends RocketSubsystemModuleImp(_outer)

class MyTest extends Bundle {
  val a0 = UInt(8.W)
  val a1 = UInt(8.W)
  val a2 = UInt(8.W)
  val a3 = UInt(8.W)
  val a4 = UInt(8.W)
}

class Test extends Module {
  val io = IO(new Bundle {
    val in = Input(new MyTest)
    val out = Output(new MyTest)
  })

  val q = Module(new Queue(new MyTest, 2))
  q.io.enq.valid := true.B
  q.io.enq.bits := io.in
  q.io.deq.ready := true.B
  io.out := q.io.deq.bits
    when (q.io.deq.fire) {
    printf("deq: %x\n", io.out.asUInt)
  }
  when (q.io.enq.fire) {
    printf("enq: %x\n", io.in.asUInt)
  }

}
class SimTop(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val logCtrl = Input(new LogCtrlIO)
    val perfInfo = new PerfInfoIO
    val uart = new UARTIO
    // val in = Input(new MyTest)
    // val out = Output(new MyTest)
  })
  io := DontCare

  val test = Module(new Test)
  test.io.in := LFSR16Seed(0x1234).asTypeOf(test.io.in)
  // io.out := test.io.out

  // val ldut = LazyModule(new ExampleFuzzSystem)
  // val dut = Module(ldut.module)

  // // Allow the debug ndreset to reset the dut, but not until the initial reset has completed
  // dut.reset := (reset.asBool | ldut.debug.map { debug => AsyncResetReg(debug.ndreset) }.getOrElse(false.B)).asBool

  // SimAXIMem.connectMem(ldut)
  // val success = WireInit(false.B)
  // Debug.connectDebug(ldut.debug, ldut.resetctrl, ldut.psd, clock, reset.asBool, success)

  // io := DontCare
  // ldut.module.meip.foreach(_.foreach(_ := false.B))
  // ldut.module.seip.foreach(_.foreach(_ := false.B))
}

class FuzzConfig extends Config(
  new WithNBigCores(1).alter((site, _, up) => {
    case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site).map {
      case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
        core = tp.tileParams.core.copy(
          haveCease = false,
          nPMPs = 0,
          nBreakpoints = 0,
          chickenCSR = false,
        )
      ))
    }
  }) ++
  new WithCoherentBusTopology ++
  new BaseConfig().alter((site, _, up) => {
    case DebugModuleKey => None
    case CLINTKey => None
    case PLICKey => None
    case BootROMLocated(InSubsystem) => Some(BootROMParams(
      contentFileName = "./bootrom/bootrom.img",
      address = 0x10000000,
      hang = 0x10000000,
      withDTB = false,
    ))
    case ExtMem => Some(MemoryPortParams(MasterPortParams(
      base = x"8000_0000",
      size = x"8000_0000",
      beatBytes = site(MemoryBusKey).beatBytes,
      idBits = 4), 1))
    case ControlBusKey => up(ControlBusKey, site).copy(
      errorDevice = None,
      zeroDevice = None,
    )
  })
)

object FuzzMain {
  def main(args: Array[String]): Unit = {
    val generator = Seq(ChiselGeneratorAnnotation(() => {
        freechips.rocketchip.diplomacy.DisableMonitors(p => new SimTop()(p))(new FuzzConfig)
      }))
    (new ChiselStage).execute(args, generator
      :+ CIRCTTargetAnnotation(CIRCTTarget.SystemVerilog)
      :+ FirtoolOption("--disable-annotation-unknown")
      :+ FirtoolOption("--preserve-aggregate=all")
    )
    DifftestModule.finish("rocket-chip")
  }
}
