#!/usr/bin/env python
# -*- coding: utf-8 -*-

import requests
from datetime import datetime, timedelta
import sys
import json
import click
import nagiosplugin

__version__ = '0.1.0'

class JobExecutions(nagiosplugin.Resource):
    def __init__(self, url, token, jobname, hours):
        self.url = url
        self.token = token
        self.jobname = jobname
        self.hours = hours

    def probe(self):
        # get JSON from scheduler application by calling REST service with job name filter
        headers = { 'Authorization' : 'Bearer {}'.format(self.token) }
        checktime = datetime.now() - timedelta(hours=self.hours)
        params = {'job':self.jobname, 'startedAfter':checktime, 'result':'SUCCESS'}
        response = requests.get(self.url, headers=headers, params=params)
        # will raise an exception if status code is not OK
        response.raise_for_status()
        # response.json() will raise an exception when there is no json in response
        yield nagiosplugin.Metric('Successful Job Executions', len(response.json()), context='JobExecutions')


@click.command()
@click.option('--url', '-u', type=click.STRING, help='URL to the scheduler REST API endpoint',required=True)
@click.option('--token', '-t', type=click.STRING, help='Token for authentification at scheduler REST service',required=True)
@click.option('--jobname', '-j', type=click.STRING, help='Name of the scheduled job to e monitored',required=True)
@click.option('--hours', '-h', type=click.INT, default=24, help='time period in hours where number of executions are checked. Default is 24')
@click.option('--warn', '-w', type=click.STRING, default='1:', help='Range definition for warning. Default is 1: (warning if less than 1)')
@click.option('--crit', '-c', type=click.STRING, default='1:', help='Range definition for critical. Default is 1: (critical if less than 1)')

@nagiosplugin.guarded
def main(warn, crit, url, token, jobname, hours):
    """ Nagios/Icinga check script for checking successful executions of scheduled jobs in appNG """
    check = nagiosplugin.Check(
        JobExecutions(url, token, jobname, hours),
        nagiosplugin.ScalarContext('JobExecutions', warn, crit))
    check.main()
    return 0


if __name__ == "__main__":
    sys.exit(main())
