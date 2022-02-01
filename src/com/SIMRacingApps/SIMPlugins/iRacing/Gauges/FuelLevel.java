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
 * @copyright Copyright (C) 2015 - 2022 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class FuelLevel extends iRacingGauge {

    State m_prevStatus;
    Data m_valueBeforePitting;
    Data m_valueNext;
    Data m_valueHistorical;
    int m_lapsHistorical;
    int m_usedCount = 1;
    double m_kgPerLiter;

    //add the non standard conversions from volume to weight
    private Data __addConversions(Data d) {
        d.addConversion("L", "KG", m_kgPerLiter);
        d.addConversion("L", "LB", new Data("",m_kgPerLiter,"KG").convertUOM("LB").getDouble());
        return d;
    }
    
    public FuelLevel(String type, iRacingCar car, Track track, IODriver IODriver, Integer driversIdx) {
        super(type, car, track, IODriver, "FuelLevel", "l", null,null);
        
        m_valueNext       = new Data(m_varName,0.0,m_iRacingUOM,Data.State.NOTAVAILABLE);
        m_valueHistorical = new Data(m_varName,0.0,m_iRacingUOM,Data.State.NOTAVAILABLE);
        m_lapsHistorical  = 0;
        
        //save off the Kg per liter of fuel that  iRacing gives us to convert between volume and weight.
        m_kgPerLiter = Double.parseDouble(IODriver.getSessionInfo().getString("DriverInfo","DriverCarFuelKgPerLtr"));
        
        //In the next build, after July 2015, David removed CarClassMaxFuel and replaced it with DriverCarFuelMaxLtr.
        //Currently, CarClassMaxFuel contains the percentage of fuel to use in this session
        
        String maxfuel         = IODriver.getSessionInfo().getString("DriverInfo","DriverCarFuelMaxLtr"); //TODO: should ask David why max fuel not in Drivers per car class?
        String maxfuelpct      = IODriver.getSessionInfo().getString("DriverInfo","Drivers",driversIdx.toString(),"CarClassMaxFuelPct");
        Data   capacityMaximum = getCapacityMaximum("l");
        double capacityPercent = 1.0;
        
        if (maxfuel.isEmpty()) //for older builds get the percentage out of CarClassMaxFuel
            maxfuel = IODriver.getSessionInfo().getString("DriverInfo","Drivers",driversIdx.toString(),"CarClassMaxFuel");
        else
            maxfuel += " l"; //this is in liters with no UOM in the data
        
        if (!maxfuel.isEmpty()) {
            String s[] = maxfuel.split("[ ]");
            if (s.length == 2) {
                if (s[1].equals("%")) {
                    capacityPercent = Double.parseDouble(s[0]);
                }
                else {
                    //this assumes, if not a percentage, David could get the max fuel for each car.
                    capacityMaximum = (new Data("",Double.parseDouble(s[0]),s[1])).convertUOM("l");
                }
            }
        }
        
        //if this not null, the we are on the new build, use it.
        if (!maxfuelpct.isEmpty()) {
            String s[] = maxfuelpct.split("[ ]");
            if (s.length == 2) {
                capacityPercent = Double.parseDouble(s[0]);
            }
        }
        
        m_prevStatus         = new State(Car.Status.OFFTRACK,0.0);
        m_valueBeforePitting = _setCapacityMaximum( capacityMaximum.getDouble() * capacityPercent, "l" );
    }

    /////// We have to override all the getters to add the conversions
    
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
        
        //convert the requested value to the iRacing UOM and round it up to the next increment
        //while keeping it within the min/max boundaries
        r.setValue(this._roundUpToIncrement(
                r.convertUOM(m_iRacingUOM).getDouble(),m_iRacingUOM
                ),m_iRacingUOM,Data.State.NORMAL);
        if (r.compare(m_valueNext) != 0) {
            _setSIMCommandTimestamp(true,Math.round(r.getDouble() + 0.5));  //always round up with Fuel to be safe
        }

        //if command is not already pending and the flag is not already set, set it
        if (this._getSIMCommandTimestamp() == 0.0 && !m_changeFlag)
            _setSIMCommandTimestamp(true,m_valueNext.convertUOM(m_iRacingUOM).getDouble());
        
        return this._getReturnValue(__addConversions(r), UOM);   
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
        
        return this._getReturnValue(__addConversions(r), UOM);   
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
        
        return this._getReturnValue(__addConversions(r), UOM);   
    }
    
    @Override
    public Data getMaximum(String UOM)           { return this._getReturnValue(__addConversions(super.getMaximum(UOM)),UOM); }
    @Override
    public Data getMajorIncrement(String UOM)    { return this._getReturnValue(__addConversions(super.getMajorIncrement(UOM)),UOM); }
    @Override
    public Data getMinorIncrement(String UOM)    { return this._getReturnValue(__addConversions(super.getMinorIncrement(UOM)),UOM); }
    @Override
    public Data getCapacityMaximum(String UOM)   { return this._getReturnValue(__addConversions(super.getCapacityMaximum(UOM)),UOM); }
    @Override
    public Data getCapacityMinimum(String UOM)   { return this._getReturnValue(__addConversions(super.getCapacityMinimum(UOM)),UOM); }
    @Override
    public Data getCapacityIncrement(String UOM) { return this._getReturnValue(__addConversions(super.getCapacityIncrement(UOM)),UOM); }
    @Override
    public Data getValueCurrent(String UOM)      { 
        return this._getReturnValue(__addConversions(super.getValueCurrent(UOM)),UOM); 
    }

    @Override 
    public Data getValueNext(String UOM) {
        Data d = super.getValueNext(UOM);
        d.setValue(m_valueNext.getDouble(),m_valueNext.getUOM(),m_valueNext.getState());
        return this._getReturnValue(__addConversions(d), UOM);
    }
    @Override 
    public Data getValueHistorical(String UOM) {
        Data d = super.getValueHistorical(UOM);
        d.setValue(m_valueHistorical.getDouble(),m_valueHistorical.getUOM(),m_valueHistorical.getState());
        return this._getReturnValue(__addConversions(d), UOM);
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
                boolean changeFlag = ((m_IODriver.getVars().getBitfield("PitSvFlags") & PitSvFlags.FuelFill) != 0);
                Data    varValue   = _readVar("PitSvFuel");
                
                //If fuel was added because we requested it to be
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
                                "FuelLevel: %f %s, %f %s, saving historical value = %f %s, %f %s",
                                _readVar().getDouble(),
                                _readVar().getUOM(),
                                _readVar().convertUOM(this.m_measurementSystem).getDouble(),
                                _readVar().convertUOM(this.m_measurementSystem).getUOM(),
                                m_valueBeforePitting.getDouble(),
                                m_valueBeforePitting.getUOM(),
                                m_valueBeforePitting.convertUOM(this.m_measurementSystem).getDouble(),
                                m_valueBeforePitting.convertUOM(this.m_measurementSystem).getUOM()
                        ));
                        
                        m_valueHistorical = new Data(m_valueBeforePitting);
                        m_lapsHistorical  = this.getLaps(currentLap).getInteger();
                        m_lapChanged      = currentLap;                 //save the lap
                        m_usedCount++;                                  //count the uses
                    }
                }

                //read the var and set the Next Value to it for the clients to display
                m_valueNext.set(varValue);
                
                this._setIsDirty(true);            

                m_changeFlag = changeFlag;
            }
            else {
                //TODO: Do I retrofit the old logic here somehow just to support my recorded files?
                //      I don't think any of the users have recorded a file.
            }
        }
        
        m_prevStatus = new State(status);
    }

    @Override
    public Data getCount() {
        Data d = super.getCount();
        d.setValue(m_usedCount,"",Data.State.NORMAL);
        return d;
    }
}
