import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AuthenticatedNetworkService } from '../shared/authenticated-network-service';
import { SchedulerData } from './scheduler-data';
import { SchedulerEvent } from './scheduler-event';
import { SchedulerProject } from './scheduler-project';

@Injectable({
  providedIn: 'root'
})
export class SchedulerNetworkService extends AuthenticatedNetworkService {

  constructor(http: HttpClient) {
    super(http);
  }

  async loadData(): Promise<SchedulerData> {
    return this.getData<SchedulerData>('/apis/user/scheduler/load');
  }

  async editProject(data: SchedulerProject): Promise<string> {
    return this.postDataForText('/apis/user/scheduler/edit?type=project', data);
  }

  async editEvent(data: SchedulerEvent): Promise<string> {
    return this.postDataForText('/apis/user/scheduler/edit?type=event', data);
  }

  async deleteRecord(key: string, type: 'project' | 'event'): Promise<string> {
    return this.deleteWithParams('/apis/user/scheduler/delete', {
      'type': type, 'key': key
    });
  }

  async markProjectAs(completed: boolean, key: string) {
    return this.postParams('/apis/user/scheduler/mark_project_as', {
      'key': key, 'completed': String(completed)
    });
  }

}
