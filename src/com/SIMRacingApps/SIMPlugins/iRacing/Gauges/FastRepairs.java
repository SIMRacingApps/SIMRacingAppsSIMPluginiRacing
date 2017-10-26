/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.PitSvFlags;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.Util.State;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class FastRepairs extends iRacingGauge {

    State m_prevStatus;
    Data m_valueHistorical;
    int m_lapsHistorical;
    Data m_valueBeforePitting;
    
    public FastRepairs(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "FastRepairsRemaining" /*TODO: Just a guess, doesn't exist*/, "", null,null);

        m_prevStatus     = new State(Car.Status.OFFTRACK,0.0);
        m_lapsHistorical = 0;
        m_valueHistorical = new Data(m_varName,0.0,m_iRacingUOM,Data.State.NOTAVAILABLE);
        m_valueBeforePitting = new Data(m_varName,0.0,m_iRacingUOM,Data.State.NOTAVAILABLE);
        m_usedCount = 0;    //don't count these until they are used
    }

    @Override
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);

        //if iRacing hasn't defined this yet, then return 0.
        if (d.getState().equals(Data.State.NOTAVAILABLE))
            d.setValue(0.0,"",Data.State.NORMAL);
        
        return this._getReturnValue(d, UOM);
    }
    
    @Override
    public Data setChangeFlag(boolean flag) {
        if (
            (flag && this._getSIMCommandTimestamp() <= 0.0)
         || (!flag && this._getSIMCommandTimestamp() >= 0.0)
         ) {
             _setSIMCommandTimestamp(flag,0.0); //Can't set the value, so just send in a zero
         }
        return getChangeFlag();
    }
    
    @Override 
    public Data getValueHistorical(String UOM) {
        Data d = super.getValueHistorical(UOM);
        d.setValue(m_valueHistorical.getDouble(),m_valueHistorical.getUOM(),m_valueHistorical.getState());
        return this._getReturnValue(d, UOM);
    }
    
    //On reset, set the change flag to the user specified default setting
    //so it won't force it to use one if they don't want it to.
    @Override
    public void _resetDetected() {
        m_changeFlag = m_IODriver.getAutoResetFastRepair() == 1;
    }
    
    @Override
    public Data getLapsHistorical() {
        Data d = super.getLapsHistorical();
        d.setValue(m_lapsHistorical);
        return d;
    }
    
    @Override
    public void _onDataVersionChange(State status, int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        super._onDataVersionChange(status, currentLap, sessionTime, lapCompletedPercent, trackLength);

        //track the value in the tank before entering the pit
        if (!status.getState().equals(Car.Status.ENTERINGPITSTALL)
        &&  !status.getState().equals(Car.Status.INPITSTALL)
        &&  !status.getState().equals(Car.Status.INGARAGE)
        &&  !status.getState().equals(Car.Status.INVALID)
        ) {
            m_valueBeforePitting = _readVar();
        }
        
        //can only read the pit values for ME and only when not in the garage
        //do not process if there are pending changes to be sent to the SIM
        if (m_car.isME() 
        && !m_car.getStatus().equals(Car.Status.INGARAGE)
//        && !((iRacingCar)m_car)._setupCommandsPending()
        && this._getSIMCommandTimestamp() == 0.0 //there's no pending command for this gauge
        ) {
            
            //check if the pit flags exist just in case a recorded file without them is played
            if (m_IODriver.getVarHeaders().getVarHeader("PitSvFlags") != null) {

                //get the current var values
                boolean changeFlag = ((m_IODriver.getVars().getBitfield("PitSvFlags") & PitSvFlags.FastRepair) != 0);
                
                //If fast repair was changed because we requested to be changed
                //and the flags changed while in the pit stall
                if (m_changeFlag 
                && !changeFlag
                && currentLap > 0
                && (   status.getState().equals(Car.Status.ENTERINGPITSTALL) 
                    || status.getState().equals(Car.Status.INPITSTALL)
                   )
                ) {
                    //don't record anything if you haven't run a lap
                    if (m_lapChanged != currentLap) {
                        
                        m_valueHistorical = new Data(m_valueBeforePitting);
                        m_lapsHistorical  = this.getLaps(currentLap).getInteger();
                        m_lapChanged      = currentLap;                 //save the lap
                        m_usedCount++;                                  //count the uses
                        
                        Server.logger().info(String.format(
                                "FastRepair: used %d",
                                m_usedCount
                        ));
                    }
                }
                
                this._setIsDirty(false);            

                m_changeFlag = changeFlag;
            }
            else {
                //TODO: Do I retrofit the old logic here somehow just to support my recorded files?
                //      I don't think any of the users have recorded a file.
            }
        }
        
        m_prevStatus = new State(status);
    }
}
