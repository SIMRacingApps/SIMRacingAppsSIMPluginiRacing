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
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.Util.State;

/**
 * This class is responsible for managing the tire pressures.
 * 
 * For the current value, it should return the value from the SIM for the tire saved when it was changed.
 * Most, if not all race cars, do not have real-time pressure monitors. 
 * iRacing doesn't output them, even if they do in real life.
 * 
 * For the next value, return what the SIM is saying the value is.
 * I will return what iRacing says, so it may be a delayed, rounded, or limited from what the user wants.
 * 
 * For the historical value, simply save off the current value of the tire that came off the car. 
 * iRacing doesn't return the actual pressure that it built up to.
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2024 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class TirePressure extends Tire {
    
    //We we send message when tire is changed
    TireTemp m_tireTempL;
    TireTemp m_tireTempM;
    TireTemp m_tireTempR;
    TireWear m_tireWearL;
    TireWear m_tireWearM;
    TireWear m_tireWearR;
    TireCompound m_tireCompound;

    public TirePressure(String type, iRacingCar car, Track track,
            IODriver IODriver, String tire,
            TireTemp tireTempL,
            TireTemp tireTempM,
            TireTemp tireTempR,
            TireWear tireWearL,
            TireWear tireWearM,
            TireWear tireWearR,
            TireCompound tireCompound
            ) {
        super(type, car, track, IODriver, "PitSv" + tire + "P", "kPa", tire);
        m_tireTempL = tireTempL;
        m_tireTempM = tireTempM;
        m_tireTempR = tireTempR;
        m_tireWearL = tireWearL;
        m_tireWearM = tireWearM;
        m_tireWearR = tireWearR;
        m_tireCompound = tireCompound;
    }

    @Override
    public Data setChangeFlag(boolean flag) {
        if (
           (flag && this._getSIMCommandTimestamp() <= 0.0)
        || (!flag && this._getSIMCommandTimestamp() >= 0.0)
        ) {
            _setSIMCommandTimestamp(flag,m_valueNext.convertUOM(m_iRacingUOM).getDouble());
        }
        return getChangeFlag();
    }
    
    @Override 
    public Data setValueNext(double d,String UOM) {
        Data r = super.setValueNext(d, this._getGaugeUOM(UOM));
        
        //we must set the value and UOM again because the super just returns the current value.
        r.setValue(d,this._getGaugeUOM(UOM));
        
        if (!m_isFixed) {
            //convert the requested value to the iRacing UOM and round it up to the next increment
            //while keeping it within the min/max boundaries
            r.setValue(this._roundUpToIncrement(
                    r.convertUOM(m_iRacingUOM).getDouble(),m_iRacingUOM
                    ),m_iRacingUOM,Data.State.NORMAL);
            if (r.compare(m_valueNext) != 0) {
                _setSIMCommandTimestamp(true,r.getDouble());
            }
        }

        //if command is not already pending and the flag is not already set, set it
        if (this._getSIMCommandTimestamp() == 0.0 && !m_changeFlag)
            _setSIMCommandTimestamp(true,m_valueNext.convertUOM(m_iRacingUOM).getDouble());
        
        return this._getReturnValue(r, UOM);   
    }
    
    @Override 
    public Data decrementValueNext(String UOM) {
        Data r = super.decrementValueNext(m_iRacingUOM);
        
        if (!m_isFixed) {
            r.setValue(this._roundUpToIncrement(
                    r.getDouble() - this.m_capacityIncrement.convertUOM(m_iRacingUOM).getDouble(), 
                    m_iRacingUOM
                    ),m_iRacingUOM,Data.State.NORMAL);
            if (r.compare(m_valueNext) != 0) {
                _setSIMCommandTimestamp(true,r.getDouble());
            }
        }
        
        //if command is not already pending and the flag is not already set, set it
        if (this._getSIMCommandTimestamp() == 0.0 && !m_changeFlag)
            _setSIMCommandTimestamp(true,m_valueNext.convertUOM(m_iRacingUOM).getDouble());
        
        return this._getReturnValue(r, UOM);   
    }
    
    @Override 
    public Data incrementValueNext(String UOM) {
        Data r = super.incrementValueNext(m_iRacingUOM);
        
        if (!m_isFixed) {
            r.setValue(this._roundUpToIncrement(
                    r.getDouble() + this.m_capacityIncrement.convertUOM(m_iRacingUOM).getDouble(),
                    m_iRacingUOM
                    ),m_iRacingUOM,Data.State.NORMAL);
            if (r.compare(m_valueNext) != 0) {
                _setSIMCommandTimestamp(true,r.getDouble());
            }
        }
        
        //if command is not already pending and the flag is not already set, set it
        if (this._getSIMCommandTimestamp() == 0.0 && !m_changeFlag)
            _setSIMCommandTimestamp(true,m_valueNext.convertUOM(m_iRacingUOM).getDouble());
        
        return this._getReturnValue(r, UOM);   
    }
    
    @Override
    public void _onDataVersionChange(State status, int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        super._onDataVersionChange(status, currentLap, sessionTime, lapCompletedPercent, trackLength);
        
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
                boolean changeFlag = ((m_IODriver.getVars().getBitfield("PitSvFlags") & PitSvFlags.getFlag(m_tire)) != 0);
                Data    varValue   = _readVar();

                m_tireCompound._tireCurrent(this);  //keep the TireCompound Gauge up to date
                
                //if current is not set, then initialize it with the first value we see
                //otherwise, it should not change until the tire is changed
                //TODO: check if dropped in from Garage, if changes made in Garage are picked up.
                //      could be dropped on pit lane, or on track when qualifying or racing.
                if (!m_valueCurrent.getState().equals(Data.State.NORMAL) 
                &&  varValue.getState().equals(Data.State.NORMAL)
                ) {
                    m_valueCurrent.set(_readVar(m_tire + "coldPressure","kPa"));
                    Server.logger().info(String.format("TirePressure%s: New Car, initializing valueCurrent = %s %f %s, %f psi",m_tire,m_tireCompound.getValueCurrent().getString(), m_valueCurrent.getDouble(),m_valueCurrent.getUOM(),m_valueCurrent.convertUOM("psi").getDouble()));
                }

                if (varValue.getState().equals(Data.State.NORMAL)) {

                    //If the tire was changed because we requested to be changed
	                //and the flags changed while in the pit stall
	                //before we read the var value, see if the tire was changed
	                //then save off the value that was on the car
	                if (m_changeFlag 
	                && !changeFlag
	                && currentLap > 0
	                && (   status.getState().equals(Car.Status.ENTERINGPITSTALL) 
	                    || status.getState().equals(Car.Status.INPITSTALL)
	                   )
	                ) {
	                    //don't record anything if you haven't run a lap
	                    if (m_lapChanged != currentLap) {
	                        Server.logger().info(String.format(
	                                "TirePressure%s: Change detected, saving valueCurrent = %f %s, %f psi",
	                                m_tire,
	                                m_valueCurrent.getDouble(),
	                                m_valueCurrent.getUOM(),
	                                m_valueCurrent.convertUOM("psi").getDouble()
	                        ));
	                        
	                        m_valueHistorical = new Data(m_valueCurrent);   //save the previous current value as historical
	                        m_lapsHistorical  = this.getLaps(currentLap).getInteger();
	                        m_lapChanged      = currentLap;                 //save the lap
	                        m_usedCount++;                                  //count the uses
	                        m_tireTempL._tireChanged(this);
	                        m_tireTempM._tireChanged(this);
	                        m_tireTempR._tireChanged(this);
	                        m_tireWearL._tireChanged(this);
	                        m_tireWearM._tireChanged(this);
	                        m_tireWearR._tireChanged(this);
	                        m_tireCompound._tireChanged(this);
	                    }
	                    
	                    m_valueCurrent    = new Data(varValue);             //save the current tire pressure
	                }
	
	                //read the var and set the Next Value to it for the clients to display
	                m_valueNext.set(varValue);
	                
	                //if the next value is different from the current value
	                //mark it dirty to be applied to next pit stop
	                this._setIsDirty(this._roundToIncrement(m_valueCurrent.convertUOM(m_UOM).getDouble(),m_UOM) != this._roundToIncrement(m_valueNext.convertUOM(m_UOM).getDouble(),m_UOM));            
	
	                m_changeFlag = changeFlag;
                }
            }
            else {
                //TODO: Do I retrofit the old logic here somehow just to support my recorded files?
                //      I don't think any of the users have recorded a file.
            }
        }
    }
}
