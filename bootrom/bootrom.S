.section .text.start, "ax", @progbits
.globl _start
_start:
  addiw t0, zero, 1
  slli t0, t0, 31
  jr t0
