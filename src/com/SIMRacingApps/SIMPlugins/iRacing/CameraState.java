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
 * @copyright Copyright (C) 2015 - 2019 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class CameraState {
    // bit fields
//    enum irsdk_CameraState
//    {
        public final static int IsSessionScreen          = 0x0001; // the camera tool can only be activated if viewing the session screen (out of car)
        public final static int IsScenicActive           = 0x0002; // the scenic camera is active (no focus car)

        //these can be changed with a broadcast message
        public final static int CamToolActive            = 0x0004;
        public final static int UIHidden                 = 0x0008;
        public final static int UseAutoShotSelection     = 0x0010;
        public final static int UseTemporaryEdits        = 0x0020;
        public final static int UseKeyAcceleration       = 0x0040;
        public final static int UseKey10xAcceleration    = 0x0080;
        public final static int UseMouseAimMode          = 0x0100;
//    };
}
