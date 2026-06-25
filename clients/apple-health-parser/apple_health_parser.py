#!/usr/bin/env python3
import yaml
from datetime import datetime
import sys
import xml.etree.ElementTree as ET
from typing import List, Dict

def apply_conversion(value:str,convert_type:str)-> float:
    value = float(value)
    if convert_type == 'lb_to_kg':
        converted_value = float(value * 0.453592)
    elif convert_type == 'fraction_to_percent':
        converted_value = float(value * 100)
    elif convert_type =='none':
        converted_value = value
    else:
        raise ValueError(f"convert_type must be lb_to_kg or fraction_to_percent, got {convert_type}")
    return converted_value

def build_point_metric_row(user_id:str,rtype:str, value:float, recorded_at:datetime, duration_seconds:int, source_name:str, config:str)-> Dict: 
    row={
        "user_id": user_id,
        "metric_type": config['point_metrics'][rtype]['metric_type'],
        "value":value,
        "recorded_at": datetime.isoformat(recorded_at),
        "duration_seconds": duration_seconds,
        "source_device": source_name
    }
    return row

def build_sleep_analysis_row(user_id:str, stage:str ,start_time:datetime, end_time:datetime, source_device:str)-> Dict:
    row = {
        "user_id": user_id,
        "stage": stage,
        "start_time" : datetime.isoformat(start_time),
        "end_time" : datetime.isoformat(end_time),
        "source_device": source_device
    }
    return row

def build_workouts_row(user_id:str, activity_type:str, start_time:datetime, end_time:datetime, duration:str, duration_unit:str, sourceName:str, energy_burned=None)-> Dict:
    row = {
        "user_id": user_id,
        "activity_type": activity_type,
        "start_time" : datetime.isoformat(start_time),
        "end_time" : datetime.isoformat(end_time),
        "source_device": sourceName,
        "duration":float(duration),
        "duration_unit":duration_unit,
        "energy_burned": energy_burned,
    }
    return row 

def build_statistics_row(type:str, startDate:datetime, endDate:datetime ,sum_value=None,average=None,min_value=None,max_value=None,unit=None)-> Dict:
    row = {
        "metric_type": type,
        "start_time" : datetime.isoformat(startDate),
        "end_time" : datetime.isoformat(endDate),
        "sum_value": sum_value,
        "average_value": average,
        "minimum_value": min_value,
        "maximum_value": max_value,
        "unit": unit
    }
    return row

with open('./mapping_config.yaml') as f:
    config = yaml.safe_load(f)

def construct_output(id:str)-> List[Dict]:
    user_id = id
    path = "/Users/runosiakpebru/Downloads/apple_health_export/export.xml" # /Users/runosiakpebru/Downloads/sample-data-health.xml
    context = ET.iterparse(source=path, events=('end',))
    context = iter(context)
    event, root = next(context)
    reject_before= datetime.strptime('2007-06-29',"%Y-%m-%d").date()
    output=list()
    sleep_output = list()
    workout_output = list()
    for event, elem in context:
        if elem.tag == 'Record':
            rtype = elem.attrib.get('type')
            if rtype in config['point_metrics']:
                sourceName= elem.attrib.get('sourceName','').replace('\xa0', ' ')
                value=elem.attrib.get('value')
                startDate=datetime.strptime(elem.attrib.get('startDate'),"%Y-%m-%d %H:%M:%S %z")
                endDate=datetime.strptime(elem.attrib.get('endDate'),"%Y-%m-%d %H:%M:%S %z")
                converted_value=apply_conversion(value,config['point_metrics'][rtype]['convert'])
                duration_seconds= float((endDate-startDate).total_seconds()) if config['point_metrics'][rtype]['has_duration'] else None
                reject_date = startDate.date() < reject_before
                if reject_date:
                    sys.stdout.write("skipping entered data as date is invalid")
                    continue
                else:
                    data_to_insert= build_point_metric_row(user_id,rtype,converted_value,startDate,duration_seconds,sourceName,config)
                    output.append(data_to_insert)
            elif rtype == config['sleep_analysis']['source_type']:
                sleep_label=elem.attrib.get('value')
                if sleep_label in config['sleep_analysis']['drop_values']:
                    continue
                else:
                    stage = config['sleep_analysis']['value_map'][sleep_label]
                    startDate= datetime.strptime(elem.attrib.get('startDate'),"%Y-%m-%d %H:%M:%S %z")
                    endDate=datetime.strptime(elem.attrib.get('endDate'),"%Y-%m-%d %H:%M:%S %z")
                    sourceName= elem.attrib.get('sourceName','').replace('\xa0', ' ')
                    reject_date = startDate.date() < reject_before
                    if reject_date:
                        sys.stdout.write("skipping entered data as date is invalid")
                        continue
                    else:
                        data_to_insert=build_sleep_analysis_row(user_id,stage,startDate,endDate,sourceName)
                        sleep_output.append(data_to_insert)
        elif elem.tag == 'Workout':
            rtype = elem.attrib.get('workoutActivityType').replace('HKWorkoutActivityType', '').lower()
            duration = elem.attrib.get('duration')
            duration_unit=elem.attrib.get('durationUnit')
            sourceName= elem.attrib.get('sourceName','').replace('\xa0', ' ')
            energy_burned= elem.attrib.get('totalEnergyBurned',None)
            startDate=datetime.strptime(elem.attrib.get('startDate'),"%Y-%m-%d %H:%M:%S %z")
            endDate=datetime.strptime(elem.attrib.get('endDate'),"%Y-%m-%d %H:%M:%S %z")
            reject_date = startDate.date() < reject_before
            if reject_date:
                sys.stdout.write("skipping entered data as date is invalid")
                continue
                
            work_out_stats=[]
            for stat_elem in elem.findall('WorkoutStatistics'):
                stat_type= config['point_metrics'].get(stat_elem.attrib.get('type'), {}).get('metric_type') or stat_elem.attrib.get('type')
                stat_start=datetime.strptime(stat_elem.attrib.get('startDate') or elem.attrib.get('startDate'),"%Y-%m-%d %H:%M:%S %z") 
                stat_end=datetime.strptime(stat_elem.attrib.get('endDate') or elem.attrib.get('endDate'),"%Y-%m-%d %H:%M:%S %z") 
                sum_value=stat_elem.attrib.get('sum',None)
                unit=stat_elem.attrib.get('unit',None)
                min_value=stat_elem.attrib.get('minimum',None)
                max_value=stat_elem.attrib.get('maximum',None)
                average=stat_elem.attrib.get('average',None)
                work_out_stats.append(build_statistics_row(stat_type,stat_start,stat_end,sum_value,average,min_value,max_value,unit))
            

            data_to_insert= build_workouts_row(user_id,rtype,startDate,endDate,duration,duration_unit,sourceName,energy_burned)
            data_to_insert['statistics'] = work_out_stats
            workout_output.append(data_to_insert)           
        if elem.tag != 'WorkoutStatistics':
            elem.clear()
    root.clear()
    return  {
    "point_metrics": output,
    "sleep_sessions": sleep_output,  
    "workouts": workout_output
}
    