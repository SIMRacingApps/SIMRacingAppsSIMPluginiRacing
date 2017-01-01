package com.SIMRacingApps.SIMPlugins.iRacing;

import java.util.logging.Level;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Remote control the sim by sending these windows messages
 * camera and replay commands only work when you are out of your car,
 * pit commands only work when in your car
 * enum irsdk_BroadcastMsg
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
public class BroadcastMsg {

    public static final short BroadcastCamSwitchPos = 0;          // car position, group, camera
    public static final short BroadcastCamSwitchNum = 1;          // driver #, group, camera
    public static final short BroadcastCamSetState = 2;           // irsdk_CameraState, unused, unused
    public static final short BroadcastReplaySetPlaySpeed = 3;    // speed, slowMotion, unused
    public static final short BroadcastReplaySetPlayPosition = 4; // irsdk_RpyPosMode, Frame Number (high, low)
    public static final short BroadcastReplaySearch = 5;          // irsdk_RpySrchMode, unused, unused
    public static final short BroadcastReplaySetState = 6;        // irsdk_RpyStateMode, unused, unused
    public static final short BroadcastReloadTextures = 7;        // irsdk_ReloadTexturesMode, carIdx, unused
    public static final short BroadcastChatComand = 8;            // irsdk_ChatCommandMode, subCommand, unused
    public static final short BroadcastPitCommand = 9;            // irsdk_PitCommandMode, parameter
    public static final short BroadcastTelemCommand = 10;         // irsdk_TelemCommandMode, unused, unused
    public static final short BroadcastFFBCommand = 11;           // irsdk_FFBCommandMode, value (float, high, low)
    public static final short BroadcastReplaySearchSessionTime=12;// sessionNum, SessionTimeMS (high,low)
    public static final short BroadcastLast = 13;                 // unused placeholder

//TODO: Implement each enum as an inner class with validation logic before calling BroadcastMsg.send().
//
//    enum irsdk_CameraState {
    public class CameraState {
        public static final short IsSessionScreen          = 0x0001; // the camera tool can only be activated if viewing the session screen (out of car)
        public static final short IsScenicActive           = 0x0002; // the scenic camera is active (no focus car)

    	//these can be changed with a broadcast message
        public static final short CamToolActive            = 0x0004;
    	public static final short UIHidden                 = 0x0008;
    	public static final short UseAutoShotSelection     = 0x0010;
    	public static final short UseTemporaryEdits        = 0x0020;
    	public static final short UseKeyAcceleration       = 0x0040;
    	public static final short UseKey10xAcceleration    = 0x0080;
    	public static final short UseMouseAimMode          = 0x0100;
    };
//
//    enum irsdk_ChatCommandMode {
    public static class ChatCommandMode {
        public static final short ChatCommand_Macro = 0;       // pass in a number from 1-15 representing the chat macro to launch
        public static final short ChatCommand_BeginChat = 1;   // Open up a new chat window
        public static final short ChatCommand_Reply = 2;       // Reply to last private chat
        public static final short ChatCommand_Cancel = 3;      // Close chat window
    };
//
//    enum irsdk_PitCommandMode				// this only works when the driver is in the car
    public static class PitCommandMode {
        public final static short PitCommand_Clear = 0;    // Clear all pit checkboxes
        public final static short PitCommand_WS = 1;       // Clean the winshield, using one tear off
        public final static short PitCommand_Fuel = 2;     // Add fuel, optionally specify the amount to add in liters or pass '0' to use existing amount
        public final static short PitCommand_LF = 3;       // Change the left front tire, optionally specifying the pressure in KPa or pass '0' to use existing pressure
        public final static short PitCommand_RF = 4;       // right front
        public final static short PitCommand_LR = 5;       // left rear
        public final static short PitCommand_RR = 6;       // right rear
        public final static short PitCommand_ClearTires=7; // Clear tire pit checkboxes
        public final static short PitCommand_FR=8;         // Request a fast Repair
        public final static short PitCommand_Last = 9;     // placeholder

        private static short _getCmd(String cmd) {
            short sCmd = -1;

            if (cmd.matches("[0-6]"))
                sCmd = Short.parseShort(cmd);
            else
            if (cmd.equalsIgnoreCase("Clear"))
                sCmd = PitCommand_Clear;
            else
            if (cmd.equalsIgnoreCase("WS"))
                sCmd = PitCommand_WS;
            else
            if (cmd.equalsIgnoreCase("Fuel"))
                sCmd = PitCommand_Fuel;
            else
            if (cmd.equalsIgnoreCase("LF"))
                sCmd = PitCommand_LF;
            else
            if (cmd.equalsIgnoreCase("RF"))
                sCmd = PitCommand_RF;
            else
            if (cmd.equalsIgnoreCase("LR"))
                sCmd = PitCommand_LR;
            else
            if (cmd.equalsIgnoreCase("RR"))
                sCmd = PitCommand_RR;
            else
            if (cmd.equalsIgnoreCase("ClearTires"))
                sCmd = PitCommand_ClearTires;
            else
            if (cmd.equalsIgnoreCase("FR"))
                sCmd = PitCommand_FR;
            return sCmd;
        }

        public static void send(IODriver IODriver, short cmd) {
            send(IODriver,cmd,0);
        }
        public static void send(IODriver IODriver, short cmd, int var1) {
            if (cmd >= 0 && cmd < PitCommand_Last)
                IODriver.broadcastMsg(BroadcastMsg.BroadcastPitCommand, cmd, var1);
        }

        public static void send(IODriver IODriver, String cmd) {
            send(IODriver,cmd,0);
        }

        public static void send(IODriver IODriver, String cmd, int var1) {
            short sCmd = _getCmd(cmd);

            if (sCmd != -1) {
                send(IODriver,sCmd, var1);
            }
        }

        public static void send(IODriver IODriver, String cmd, String var1) {
            int iVar1 = 0;
            try {
                iVar1 = Integer.parseInt(var1);
            }
            catch (NumberFormatException e) {}
            send(IODriver,cmd,iVar1);
        }

        public static void send(IODriver IODriver, String cmd, String var1, String UOM) {
            Data d = new Data(cmd, 0);
            try {
                d.setValue(Integer.parseInt(var1));
                d.setUOM(UOM);
            }
            catch (NumberFormatException e) {}
            send(IODriver,cmd,d);
        }

        public static void send(IODriver IODriver, String cmd, Data data) {
            short sCmd = _getCmd(cmd);
            send(IODriver,sCmd,data);
        }

        public static void send(IODriver IODriver,short cmd, Data data) {
            if (cmd == PitCommand_Fuel) {
                //round up to make sure we get enough fuel
                send(IODriver,cmd,(short)Math.ceil(data.convertUOM("l").getFloat()));
            }
            else
            if (cmd == PitCommand_LF || cmd == PitCommand_LR || cmd == PitCommand_RF || cmd == PitCommand_RR) {
                send(IODriver,cmd, (short)Math.round(data.convertUOM("kPa").getFloat()));
            }
        }
    }
//
//    enum irsdk_TelemCommandMode {			// You can call this any time, but telemtry only records when driver is in there car
    public static class TelemCommandMode {
        public static final short TelemCommand_Stop = 0;		// Turn telemetry recording off
    	public static final short TelemCommand_Start = 1;		// Turn telemetry recording on
    	public static final short TelemCommand_Restart = 2;		// Write current file to disk and start a new one
    };
//
//    enum irsdk_RpyStateMode {
    public static class RpyStateMode {
        public static final short RpyState_EraseTape = 0;		// clear any data in the replay tape
    	public static final short RpyState_Last = 1;			// unused place holder
    };
//
//    enum irsdk_ReloadTexturesMode {
    public static class ReloadTexturesMode {
        public static final short ReloadTextures_All = 0;		// reload all textures
        public static final short ReloadTextures_CarIdx = 1;	// reload only textures for the specific carIdx
    };
//
//    // Search replay tape for events
//    enum irsdk_RpySrchMode {
    public static class RpySrchMode {
        public static final short RpySrch_ToStart = 0;
        public static final short RpySrch_ToEnd = 1;
        public static final short RpySrch_PrevSession = 2;
        public static final short RpySrch_NextSession = 3;
        public static final short RpySrch_PrevLap = 4;
        public static final short RpySrch_NextLap = 5;
        public static final short RpySrch_PrevFrame = 6;
        public static final short RpySrch_NextFrame = 7;
        public static final short RpySrch_PrevIncident = 8;
        public static final short RpySrch_NextIncident = 9;
        public static final short RpySrch_Last = 10;                   // unused placeholder
    };
//
//    enum irsdk_RpyPosMode {
    public static class RpyPosMode {
        public static final short RpyPos_Begin = 0;
        public static final short RpyPos_Current = 1;
        public static final short RpyPos_End = 2;
        public static final short RpyPos_Last = 3;                  // unused placeholder
    };
    
//    enum irsdk_FFBCommandMode               // You can call this any time
    public static class FFBCommandMode 
    {
        public static final short irsdk_FFBCommand_MaxForce = 0;    // Set the maximum force when mapping steering torque force to direct input units (float in Nm)
        public static final short irsdk_FFBCommand_Last     = 1;    // unused placeholder
    };
    
//
//    // irsdk_BroadcastCamSwitchPos or irsdk_BroadcastCamSwitchNum camera focus defines
//    // pass these in for the first parameter to select the 'focus at' types in the camera system.
//    enum irsdk_csMode
    public class csMode
    {
    	public final static short csFocusAtIncident = -3;
    	public final static short csFocusAtLeader   = -2;
    	public final static short csFocusAtExiting  = -1;
    	// ctFocusAtDriver + car number...
    	public final static short csFocusAtDriver   = 0;
    };

    public static void send(IODriver IODriver, String msg) {
        send(IODriver,msg,"","","");
    }
    public static void send(IODriver IODriver, String msg, String var1) {
        send(IODriver,msg,var1,"","");
    }
    public static void send(IODriver IODriver, String msg, String var1, String var2) {
        send(IODriver,msg,var1,var2,"");
    }

    public static String send(IODriver IODriver, String msg, String var1, String var2, String var3) {
        String s = "";
        short sMsg = -1;

        if (msg.matches("[0-9]"))
            sMsg = Short.parseShort(msg);
        else
        if (msg.equalsIgnoreCase("CamSwitchPos"))
            sMsg = BroadcastCamSwitchPos;
        else
        if (msg.equalsIgnoreCase("CamSwitchNum"))
            sMsg = BroadcastCamSwitchNum;
        else
        if (msg.equalsIgnoreCase("CamSetState"))
            sMsg = BroadcastCamSetState;
        else
        if (msg.equalsIgnoreCase("ReplaySetPlaySpeed"))
            sMsg = BroadcastReplaySetPlaySpeed;
        else
        if (msg.equalsIgnoreCase("ReplaySetPlayPosition"))
            sMsg = BroadcastReplaySetPlayPosition;
        else
        if (msg.equalsIgnoreCase("ReplaySearch"))
            sMsg = BroadcastReplaySearch;
        else
        if (msg.equalsIgnoreCase("ReplaySetState"))
            sMsg = BroadcastReplaySetState;
        else
        if (msg.equalsIgnoreCase("ReloadTextures"))
            sMsg = BroadcastReloadTextures;
        else
        if (msg.equalsIgnoreCase("ChatComand"))
            sMsg = BroadcastChatComand;
        else
        if (msg.equalsIgnoreCase("PitCommand"))
            sMsg = BroadcastPitCommand;
        else
        if (msg.equalsIgnoreCase("TelemCommand"))
            sMsg = BroadcastTelemCommand;
        else
        if (msg.equalsIgnoreCase("FFBCommand"))
            sMsg = BroadcastFFBCommand;
        else
        if (msg.equalsIgnoreCase("ReplaySearchSessionTime"))
            sMsg = BroadcastReplaySearchSessionTime;

        if (sMsg == BroadcastPitCommand) {
            PitCommandMode.send(IODriver,var1,var2,var3);
        }
        if (sMsg < 0 || sMsg >= BroadcastLast ) {
            Server.logger().warning(s = msg + ": Invalid Broadcast Message");
        }
        else {
            //TODO: send the message in blind for now. Add logic for other commands and error checking
            try {
                s = msg + "/" + var1 + "/" + var2 + "/" + var3;
                
                short v1 = var1.isEmpty() ? 0 : Short.parseShort(var1);
                short v2 = var2.isEmpty() ? 0 : Short.parseShort(var2);
                
                if (var3.isEmpty()) {
                    IODriver.broadcastMsg(sMsg, v1, v2);
                }
                else {
                    IODriver.broadcastMsg(sMsg, v1, Short.parseShort(var2), Short.parseShort(var3));
                }
            }
            catch (NumberFormatException e) {
                Server.logStackTrace(Level.SEVERE,s = s + " = NumberFormatException: "+ e.getMessage(),e);
            }
        }
        return s;
    }
}
