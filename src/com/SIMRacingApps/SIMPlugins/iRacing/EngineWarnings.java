package com.SIMRacingApps.SIMPlugins.iRacing;

//Copyright (c) 2013, iRacing.com Motorsport Simulations, LLC.
//All rights reserved.
//THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
//ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
//WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
//ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
//(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
//LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
//ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
//(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
//SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
public class EngineWarnings {
	// bit fields
	//enum irsdk_EngineWarnings 
	//{
	public final static int waterTempWarning       = 0x01;
	public final static int fuelPressureWarning    = 0x02;
	public final static int oilPressureWarning     = 0x04;
	public final static int engineStalled          = 0x08;
	public final static int pitSpeedLimiter        = 0x10;
	public final static int revLimiterActive       = 0x20;
	//};
}
