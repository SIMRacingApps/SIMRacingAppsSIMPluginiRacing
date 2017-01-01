package com.SIMRacingApps.SIMPlugins.iRacing;

/**
 * Copyright (c) 2013, iRacing.com Motorsport Simulations, LLC.
 * All rights reserved.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class VarType {

	public final static int IRSDK_MAX_STRING = 32;
	// descriptions can be longer than max_string!
	public final static int IRSDK_MAX_DESC = 64; 

	//enum irsdk_VarType
	//{
	    // 1 byte
		public final static int irsdk_char = 0;
		public final static int irsdk_bool = 1;

	    // 4 bytes
		public final static int irsdk_int = 2;
		public final static int irsdk_bitField = 3;
		public final static int irsdk_float = 4;

	    // 8 bytes
		public final static int irsdk_double = 5;

	    //index, don't use
	    public final static int irsdk_ETCount = 6;
	//};

	//#static const int irsdk_VarTypeBytes[irsdk_ETCount] =
	private final static int[] m_bytes = 
	{
	    1,        // irsdk_char
	    1,        // irsdk_bool
	    4,        // irsdk_int
	    4,        // irsdk_bitField
	    4,        // irsdk_float
	    8,        // irsdk_double
	};

	private final static String[] m_names = 
	{
	    "char",       // irsdk_char
	    "bool",       // irsdk_bool

	    "int",        // irsdk_int
	    "bitField",   // irsdk_bitField
	    "float",      // irsdk_float

	    "double",     // irsdk_double
	};

	private int m_type;
	
	public VarType (int type) {
		m_type = type;
	}
	
	public int getType() { return m_type; }
	public int getSize() { return m_bytes[m_type]; }
	public String getTypeName() { return m_names[m_type]; }
}
