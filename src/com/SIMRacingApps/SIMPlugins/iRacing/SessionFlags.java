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
public class SessionFlags {
	//enum irsdk_Flags
	//{
	    // global flags
	    public final static int checkered      = 0x00000001;
	    public final static int white          = 0x00000002;
	    public final static int green          = 0x00000004;
	    public final static int yellow         = 0x00000008;
	    public final static int red            = 0x00000010;
	    public final static int blue           = 0x00000020;
	    public final static int debris         = 0x00000040;
	    public final static int crossed        = 0x00000080;
	    public final static int yellowWaving   = 0x00000100;
	    public final static int oneLapToGreen  = 0x00000200;
	    public final static int greenHeld      = 0x00000400;
	    public final static int tenToGo        = 0x00000800;
	    public final static int fiveToGo       = 0x00001000;
	    public final static int randomWaving   = 0x00002000;
	    public final static int caution        = 0x00004000;
	    public final static int cautionWaving  = 0x00008000;

	    // drivers black flags
	    public final static int black          = 0x00010000;
	    public final static int disqualify     = 0x00020000;
	    public final static int servicible     = 0x00040000; // car is allowed service (not a flag)
	    public final static int furled         = 0x00080000;
	    public final static int repair         = 0x00100000;

	    // start lights
	    public final static int startHidden    = 0x10000000;
	    public final static int startReady     = 0x20000000;
	    public final static int startSet       = 0x40000000;
	    public final static int startGo        = 0x80000000;
	//};
}
