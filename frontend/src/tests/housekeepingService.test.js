import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as service from '../services/housekeepingService.js';
import { apiRequest, ApiError } from '../services/apiClient.js';
vi.mock('../services/apiClient.js', async original => ({...await original(), apiRequest: vi.fn()}));
beforeEach(() => vi.clearAllMocks());
describe('Small housekeeping API adapter', () => {
  const room = { id: 1, roomNumber: '101', floor: 1, status: 'DIRTY', active: true };
  const worker = { id: 1, name: 'Synthetic worker', email: 'worker@example.test', role: 'USER', active: true };
  const task = { id: 1, title: 'Clean 101', status: 'ASSIGNED', priority: 'HIGH', assignedUser: worker, room, dueAt: null, completedAt: null };
  const inspection = { id: 1, roomId: 1, taskId: 1, inspectedBy: worker, result: 'PASS', inspectedAt: '2026-07-01T12:00:00' };
  const readers = [service.listTasks, service.listRooms, service.listUsers, () => service.listInspections(1)];
  it.each(readers.flatMap((read, index) => [null, {}, 'not an array'].map(value => ({read, index, value}))))('rejects non-array response $index / $value safely', async ({read,value}) => {
    vi.mocked(apiRequest).mockResolvedValue(value);
    await expect(read()).rejects.toBeInstanceOf(ApiError);
    await expect(read()).rejects.toMatchObject({status:502});
  });
  it.each([
    {read:service.listTasks,value:{...task,room:null}},
    {read:service.listTasks,value:{...task,assignedUser:undefined}},
    {read:service.listTasks,value:{...task,dueAt:42}},
    {read:service.listTasks,value:{...task,priority:'INVALID'}},
    {read:service.listRooms,value:{...room,floor:'1'}},
    {read:service.listRooms,value:{...room,active:'true'}},
    {read:service.listUsers,value:{...worker,name:null}},
    {read:service.listUsers,value:{...worker,role:'STAFF'}},
    {read:()=>service.listInspections(1),value:{...inspection,inspectedBy:null}},
    {read:()=>service.listInspections(1),value:{...inspection,inspectedAt:42}},
  ])('rejects malformed essential fields %# without echoing data', async ({read,value}) => {
    vi.mocked(apiRequest).mockResolvedValue([value]);
    await expect(read()).rejects.toMatchObject({status:502,message:'The service is unavailable. Please try again.'});
  });
  it.each([{read:service.listTasks,value:task},{read:service.listRooms,value:room},{read:service.listUsers,value:worker},{read:()=>service.listInspections(1),value:inspection}])('accepts valid essential response %#', async ({read,value}) => {
    vi.mocked(apiRequest).mockResolvedValue([value]);
    expect(await read()).toEqual([value]);
  });
  it('reads only frozen list/history routes with a cancellable request', async () => {
    vi.mocked(apiRequest).mockResolvedValue([]); const signal = new AbortController().signal;
    await service.listTasks(signal); await service.listRooms(signal); await service.listUsers(signal); await service.listInspections(1,signal);
    expect(vi.mocked(apiRequest).mock.calls.map(call => call[0])).toEqual(['/tasks','/rooms','/users','/rooms/1/inspections']);
    expect(apiRequest).toHaveBeenLastCalledWith('/rooms/1/inspections',{signal});
  });
  it('uses plain POST/PUT/DELETE and exact status/inspection bodies', async () => {
    await service.save('rooms',null,{roomNumber:'101'}); await service.save('tasks',1,{title:'Clean'}); await service.deactivate('users',2);
    await service.taskStatus(3,'COMPLETED'); await service.roomStatus(1,'DIRTY'); await service.inspect(1,3,'PASS','Checked');
    const calls = vi.mocked(apiRequest).mock.calls;
    expect(calls.map(c => [c[0],c[1]?.method])).toEqual([['/rooms','POST'],['/tasks/1','PUT'],['/users/2','DELETE'],['/tasks/3/status','PUT'],['/rooms/1/status','PUT'],['/rooms/1/inspections','POST']]);
    expect(calls[2][1]?.body).toBeUndefined();
    expect(JSON.parse(String(calls[5][1]?.body))).toEqual({taskId:3,result:'PASS',notes:'Checked'});
  });
  it('uses strict overdue boundary and excludes terminal tasks', () => {
    const basic = /** @type {import('../services/housekeepingService.js').Task} */ ({dueAt:'2026-07-01T08:30:00',status:'ASSIGNED'});
    expect(service.overdue(basic,'2026-07-01T08:30:00')).toBe(false);
    expect(service.overdue(basic,'2026-07-01T08:30:01')).toBe(true);
    expect(service.overdue({...basic,status:'COMPLETED'},'2026-07-01T08:30:01')).toBe(false);
    expect(service.overdue({...basic,status:'CANCELLED'},'2026-07-01T08:30:01')).toBe(false);
    expect(service.overdue({...basic,dueAt:null},'2026-07-01T08:30:01')).toBe(false);
  });
  it('labels USER without changing authorization and formats naive hotel timestamps', () => {
    expect(service.roleLabel('USER')).toBe('Housekeeper'); expect(service.roleLabel('MANAGER')).toBe('Manager');
    expect(service.roleLabel('ADMIN')).toBe('Admin'); expect(service.displayTime(null)).toBe('Not set');
    expect(service.displayTime('2026-07-01T08:30:00')).toBe('2026-07-01 08:30 (hotel local time)');
  });
});
