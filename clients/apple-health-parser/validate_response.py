from typing import Dict,Any
from datetime import datetime

VALID_METRIC_TYPES = {
    'heart_rate', 'resting_heart_rate', 'hrv', 'step_count',
    'active_energy_burned', 'apple_exercise_time', 'distance_walking_running',
    'flights_climbed', 'basal_energy_burned', 'body_mass',
    'vo2_max', 'oxygen_saturation', 'respiratory_rate'
}

VALID_SLEEP_STAGES = {'core', 'deep', 'rem', 'unspecified'}

def validate_output(output: Dict) -> Dict[str,Any]:
    errors_dict = {
    'point_metrics_missing_user_id': 0,
    'point_metrics_invalid_metric_type': [],
    'point_metrics_value_error': 0,
    'point_metrics_recorded_at_error': 0,
    'point_metrics_duration_error': 0,
    'point_metrics_source_device_error': 0,
    'sleep_sessions_missing_user_id': 0,
    'sleep_sessions_unknown_stage': [],
    'sleep_sessions_time_issue': 0,
    'sleep_sessions_source_device_error': 0,
    'workouts_missing_user_id': 0,
    'workouts_time_issue': 0,
    'workouts_duration_error': 0,
    'workouts_energy_burned_error': 0,
    'workouts_statistics_error': 0,
    'workouts_statistics_all_null': 0,
}   
    def validate_point_metrics():
        for i in range (0,len(output['point_metrics'])):
            if not output['point_metrics'][i]['user_id']:
                errors_dict['point_metrics_missing_user_id']= errors_dict.get('point_metrics_missing_user_id',0)+1
            if output['point_metrics'][i]['metric_type'] not in VALID_METRIC_TYPES:
                errors_dict['point_metrics_invalid_metric_type'].append(output['point_metrics'][i]['metric_type'])
            if output['point_metrics'][i]['value'] is None or isinstance(output['point_metrics'][i]['value'],(int,float)) is False:
                errors_dict['point_metrics_value_error']= errors_dict.get('point_metrics_value_error',0)+1
            if isinstance(output['point_metrics'][i]['recorded_at'],str) is False: 
                errors_dict['point_metrics_recorded_at_error']= errors_dict.get('point_metrics_recorded_at_error',0)+1
            if output['point_metrics'][i]['duration_seconds'] is not None and output['point_metrics'][i]['duration_seconds'] < 0:
                errors_dict['point_metrics_duration_error']= errors_dict.get('point_metrics_duration_error',0)+1
            if not output['point_metrics'][i]['source_device'] or not isinstance(output['point_metrics'][i]['source_device'],str):
                errors_dict ['point_metrics_source_device_error'] = errors_dict.get('point_metrics_source_device_error',0)+1
    
    def validate_sleep_sessions():
        for i in range (0,len(output['sleep_sessions'])):
            if not output['sleep_sessions'][i]['user_id']:
                errors_dict['sleep_sessions_missing_user_id']= errors_dict.get('sleep_sessions_missing_user_id',0)+1
            if output['sleep_sessions'][i]['stage'] not in VALID_SLEEP_STAGES:
                errors_dict['sleep_sessions_unknown_stage'].append(output['sleep_sessions'][i]['stage'])        
            if output['sleep_sessions'][i]['start_time'] and output['sleep_sessions'][i]['end_time']:
                start = datetime.fromisoformat(output['sleep_sessions'][i]['start_time'])
                end = datetime.fromisoformat(output['sleep_sessions'][i]['end_time'])
                if start >= end:
                    errors_dict['sleep_sessions_time_issue']= errors_dict.get('sleep_sessions_time_issue',0)+1
            if not output['sleep_sessions'][i]['source_device'] or not isinstance(output['sleep_sessions'][i]['source_device'],str):
                errors_dict ['sleep_sessions_source_device_error'] = errors_dict.get('sleep_sessions_source_device_error',0)+1

    def validate_workouts():
        for i in range (0,len(output['workouts'])):
            if not output['workouts'][i]['user_id']:
                errors_dict['workouts_missing_user_id']= errors_dict.get('workouts_missing_user_id',0)+1
            if output['workouts'][i]['start_time'] and output['workouts'][i]['end_time']:
                start = datetime.fromisoformat(output['workouts'][i]['start_time'])
                end = datetime.fromisoformat(output['workouts'][i]['end_time'])
                if start >= end:
                    errors_dict['workouts_time_issue']= errors_dict.get('workouts_time_issue',0)+1
            if float(output['workouts'][i]['duration']) < 0:
                errors_dict['workouts_duration_error']= errors_dict.get('workouts_duration_error',0)+1
            energy_burned = output['workouts'][i].get('energy_burned')
            if energy_burned is not None and energy_burned < 0:
                errors_dict['workouts_energy_burned_error'] += 1
            if not isinstance(output['workouts'][i]['statistics'],list) :
                errors_dict['workouts_statistics_error']= errors_dict.get('workouts_statistics_error',0)+1
            for stat in output['workouts'][i]['statistics']:
                all_null = all(
                    stat.get(field) is None
                    for field in ['sum_value', 'average_value', 'minimum_value', 'maximum_value']
                )
                if all_null:
                    errors_dict['workouts_statistics_all_null'] = errors_dict.get('workouts_statistics_all_null',0)+1
    validate_point_metrics()
    validate_sleep_sessions()
    validate_workouts()
    return errors_dict