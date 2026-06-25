#!/usr/bin/env python3
from apple_health_parser import construct_output
from validate_response import validate_output
import sys
import argparse
import json

OUTPUT_PATH= './output/full_output.json'

fatal_keys = {
    'point_metrics_missing_user_id',
    'point_metrics_invalid_metric_type',
    'point_metrics_value_error',
    'point_metrics_recorded_at_error',
    'point_metrics_duration_error',
    'point_metrics_source_device_error',
    'sleep_sessions_missing_user_id',
    'sleep_sessions_unknown_stage',
    'sleep_sessions_time_issue',
    'sleep_sessions_source_device_error',
    'workouts_missing_user_id',
    'workouts_time_issue',
    'workouts_duration_error',
    'workouts_statistics_error',
    }
    
informational_keys = {
'workouts_energy_burned_error',
'workouts_statistics_all_null',
}


def create_parser()-> argparse.ArgumentParser: 
    parser = argparse.ArgumentParser()
    parser.add_argument("-i", "--user_id", type=str, required=True, help="User ID to tag all records with")
    parser.add_argument("-o","--output",type=str, default=OUTPUT_PATH, help=f"Output file path (default: {OUTPUT_PATH})")
    return parser

     
def has_error(val) -> bool:
    return (isinstance(val, int) and val > 0) or (isinstance(val, list) and len(val) > 0)
    

def main()-> None:
    parser = create_parser()
    args = parser.parse_args()

    output=construct_output(args.user_id)
    errors=validate_output(output)

    fatal_errors = {k: v for k, v in errors.items() if k in fatal_keys and has_error(v)}
    info_errors = {k: v for k, v in errors.items() if k in informational_keys and has_error(v)}

    if info_errors:
        sys.stdout.write("\nWarnings (non-fatal):\n")
        for k, v in info_errors.items():
            sys.stdout.write(f" {k}: {v}\n")
    
    if fatal_errors:
        sys.stderr.write("\nFatal validation errors — data not sent:\n")
        for k, v in fatal_errors.items():
            sys.stderr.write(f" {k}: {v}\n")
        sys.exit(1)

    with open('./output/full_output1.json', 'w') as f:
        json.dump(output, f, indent=2)
    sys.stdout.write(
        f"Done.\n"
        f"id: {output['point_metrics'][0]['user_id']} \n"
        f"{len(output['point_metrics'])} point_metrics, \n"
        f"{len(output['sleep_sessions'])} sleep_sessions, \n"
        f"{len(output['workouts'])} workouts \n"
        f"written to {args.output}\n"
    )
    
if __name__ == "__main__":
    sys.exit(main())


    

