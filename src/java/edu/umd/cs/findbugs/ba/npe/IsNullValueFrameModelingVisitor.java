/*
 * Bytecode Analysis Framework
 * Copyright (C) 2003-2005 University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.ba.npe;

import org.apache.bcel.generic.*;

import edu.umd.cs.findbugs.ba.AbstractFrameModelingVisitor;
import edu.umd.cs.findbugs.ba.AssertionMethods;
import edu.umd.cs.findbugs.ba.DataflowAnalysisException;
import edu.umd.cs.findbugs.ba.InvalidBytecodeException;

public class IsNullValueFrameModelingVisitor extends AbstractFrameModelingVisitor<IsNullValue, IsNullValueFrame> {

	private static final boolean NO_ASSERT_HACK = Boolean.getBoolean("inva.noAssertHack");

	private AssertionMethods assertionMethods;
	private MayReturnNullPropertyDatabase mayReturnNullDatabase;

	public IsNullValueFrameModelingVisitor(ConstantPoolGen cpg, AssertionMethods assertionMethods) {
		super(cpg);
		this.assertionMethods = assertionMethods;
	}
	
	public void setMayReturnNullDatbase(MayReturnNullPropertyDatabase mayReturnNullDatabase) {
		this.mayReturnNullDatabase = mayReturnNullDatabase;
	}

	public IsNullValue getDefaultValue() {
		return IsNullValue.nonReportingNotNullValue();
	}

	// Overrides of specific instruction visitor methods.
	// ACONST_NULL obviously produces a value that is DEFINITELY NULL.
	// LDC produces values that are NOT NULL.
	// NEW produces values that are NOT NULL.

	// Note that all instructions that have an implicit null
	// check (field access, invoke, etc.) are handled in IsNullValueAnalysis,
	// because handling them relies on control flow (the existence of
	// an ETB and exception edge prior to the block containing the
	// instruction with the null check.)

	// Note that we don't override IFNULL and IFNONNULL.
	// Those are handled in the analysis itself, because we need
	// to produce different values in each of the control successors.

	private void produce(IsNullValue value) {
		IsNullValueFrame frame = getFrame();
		frame.pushValue(value);
	}

	private void produce2(IsNullValue value) {
		IsNullValueFrame frame = getFrame();
		frame.pushValue(value);
		frame.pushValue(value);
	}

	/**
	 * Handle method invocations.
	 * Generally, we want to get rid of null information following a
	 * call to a likely exception thrower or assertion.
	 */
	private void handleInvoke(InvokeInstruction obj) {
		Type callType = obj.getLoadClassType(getCPG());
		Type returnType = obj.getReturnType(getCPG());
		
		boolean stringMethodCall = callType.equals(Type.STRING) && returnType.equals(Type.STRING);
		
		// Determine if we are going to model the return value of this call.
		boolean modelCallReturnValue =
			   stringMethodCall
			|| IsNullValueAnalysis.UNKNOWN_VALUES_ARE_NSP;
		
		if( !modelCallReturnValue) {
			// Normal case: Assume returned values are non-reporting non-null.
			handleNormalInstruction(obj);
		} else {
			// Special case: some special value is pushed on the stack for the return value
			IsNullValue pushValue = null;
			
			if (stringMethodCall) {
				// String methods always return a non-null value
				pushValue = IsNullValue.nonNullValue();
			} else if (IsNullValueAnalysis.UNKNOWN_VALUES_ARE_NSP) {
				pushValue = (returnType instanceof ReferenceType)
					? IsNullValue.nullOnSimplePathValue()
					: IsNullValue.nonReportingNotNullValue();
			}
			
			modelInstruction(obj, getNumWordsConsumed(obj), getNumWordsProduced(obj), pushValue);
		}

		if (!NO_ASSERT_HACK) {
			if (assertionMethods.isAssertionCall(obj)) {
				IsNullValueFrame frame = getFrame();
				for (int i = 0; i < frame.getNumSlots(); ++i) {
					IsNullValue value = frame.getValue(i);
					if (value.isDefinitelyNull() || value.isNullOnSomePath()) {
						frame.setValue(i, IsNullValue.nonReportingNotNullValue());
					}
				}
			}
		}
	}

	public void visitACONST_NULL(ACONST_NULL obj) {
		produce(IsNullValue.checkedNullValue());
	}

	public void visitNEW(NEW obj) {
		produce(IsNullValue.checkedNonNullValue());
	}

	public void visitLDC(LDC obj) {
		produce(IsNullValue.checkedNonNullValue());
	}

	public void visitLDC_W(LDC_W obj) {
		produce(IsNullValue.checkedNonNullValue());
	}

	public void visitLDC2_W(LDC2_W obj) {
		produce2(IsNullValue.checkedNonNullValue());
	}

	public void visitINVOKESTATIC(INVOKESTATIC obj) {
		handleInvoke(obj);
	}

	public void visitINVOKESPECIAL(INVOKESPECIAL obj) {
		handleInvoke(obj);
	}

	public void visitINVOKEINTERFACE(INVOKEINTERFACE obj) {
		handleInvoke(obj);
	}

	public void visitINVOKEVIRTUAL(INVOKEVIRTUAL obj) {
		handleInvoke(obj);
	}

}

// vim:ts=4
