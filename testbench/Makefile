# cocotb testbench for TopCordic2
#
# Prerequisites:
#   pip install cocotb
#   sudo apt install iverilog          # or verilator, ghdl, etc.
#
# Generate the Verilog first (from repo root):
#   sbt "runMain Cordic2.TopCordic2Driver --target-dir testbench"
#
# Then run:
#   cd testbench && make
#   make clean     # remove sim artefacts

SIM            ?= icarus
TOPLEVEL_LANG   = verilog

# Verilog emitted by TopCordic2Driver into this directory
VERILOG_SOURCES = $(PWD)/TopCordic2.v

TOPLEVEL = TopCordic2
MODULE   = test_cordic2

include $(shell cocotb-config --makefiles)/Makefile.sim
