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

public class PitSvFlags {
    // bit fields
    //enum irsdk_PitSvFlags
    //{
    public final static int LFTireChange      = 0x0001;
    public final static int RFTireChange      = 0x0002;
    public final static int LRTireChange      = 0x0004;
    public final static int RRTireChange      = 0x0008;
    public final static int FuelFill          = 0x0010;
    public final static int WindshieldTearoff = 0x0020;
    public final static int FastRepair        = 0x0040;
    //};
    
    public final static int getFlag(String flagName) {
        if (flagName.equalsIgnoreCase("LF") || flagName.equalsIgnoreCase("LFTireChange"))
            return LFTireChange;
        else
        if (flagName.equalsIgnoreCase("LR") || flagName.equalsIgnoreCase("LRTireChange"))
            return LRTireChange;
        else
        if (flagName.equalsIgnoreCase("RF") || flagName.equalsIgnoreCase("RFTireChange"))
            return RFTireChange;
        else
        if (flagName.equalsIgnoreCase("RR") || flagName.equalsIgnoreCase("RRTireChange"))
            return RRTireChange;
        else
        if (flagName.equalsIgnoreCase("Fuel") || flagName.equalsIgnoreCase("FuelFill"))
            return FuelFill;
        else
        if (flagName.equalsIgnoreCase("WS") || flagName.equalsIgnoreCase("WindshieldTearoff"))
            return WindshieldTearoff;
        else
        if (flagName.equalsIgnoreCase("FR") || flagName.equalsIgnoreCase("FastRepair"))
            return FastRepair;
        return 0;
    }
}
