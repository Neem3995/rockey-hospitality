import { act, render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { TestAuth, testUser } from './authTestHelpers.jsx';
import { ApiError } from '../services/apiClient.js';
import * as service from '../services/housekeepingService.js';

vi.mock('../services/housekeepingService.js', async original => ({
  ...await original(), listTasks: vi.fn(), listRooms: vi.fn(), listUsers: vi.fn(),
  listInspections: vi.fn(), save: vi.fn(), deactivate: vi.fn(), taskStatus: vi.fn(), roomStatus: vi.fn(), inspect: vi.fn(),
}));
const time = '2026-07-01T08:30:00';
/** @type {import('../services/housekeepingService.js').Room} */
const room = { id: 1, roomNumber: '101', floor: 1, status: 'DIRTY', active: true, createdAt: time, updatedAt: time };
/** @type {import('../services/housekeepingService.js').Task} */
const task = { id: 1, title: 'Clean 101', description: 'Clean all surfaces', status: 'ASSIGNED', priority: 'HIGH',
  assignedUser: { id: 1, name: 'Synthetic housekeeper' }, room, dueAt: '2020-07-01T08:00:00', completedAt: null, createdAt: time, updatedAt: time };
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(service.listTasks).mockResolvedValue([task]);
  vi.mocked(service.listRooms).mockResolvedValue([room]);
  vi.mocked(service.listUsers).mockResolvedValue([{ ...testUser(), name: 'Synthetic housekeeper' }]);
  vi.mocked(service.listInspections).mockResolvedValue([]);
  vi.mocked(service.save).mockResolvedValue({});
  vi.mocked(service.deactivate).mockResolvedValue(undefined);
  vi.mocked(service.taskStatus).mockResolvedValue({});
  vi.mocked(service.roomStatus).mockResolvedValue({});
  vi.mocked(service.inspect).mockResolvedValue({});
});
/** @param {string} path @param {import('../routes/routeDefinitions.js').Role} [role] */
function page(path, role = 'MANAGER') {
  return render(<MemoryRouter initialEntries={[path]}><TestAuth value={{ status: 'authenticated', user: testUser(role), sessionKey: 1 }}><App /></TestAuth></MemoryRouter>);
}
function deferred() {
  /** @type {(value:any)=>void} */ let resolve = () => {};
  const promise = new Promise(done => { resolve = done; });
  return { resolve, promise };
}
describe('Housekeeper data boundary and work', () => {
  it('USER dashboard derives only assigned work and never requests management data', async () => {
    page('/dashboard','USER');
    expect(await screen.findByRole('heading',{name:'Assigned tasks'})).toBeTruthy();
    expect(service.listRooms).not.toHaveBeenCalled(); expect(service.listUsers).not.toHaveBeenCalled();
    expect(screen.queryByRole('heading',{name:'READY rooms'})).toBeNull();
    expect(within(screen.getByRole('heading',{name:'Overdue tasks'}).parentElement || document.body).getByText('1')).toBeTruthy();
  });
  it.each(['MANAGER','ADMIN'])('%s dashboard includes authorized room status totals', async role => {
    page('/dashboard', /** @type {import('../routes/routeDefinitions.js').Role} */ (role));
    expect(await screen.findByRole('heading',{name:'DIRTY rooms'})).toBeTruthy();
    expect(service.listRooms).toHaveBeenCalledTimes(1);
  });
  it('renders zero dashboard counts', async () => {
    vi.mocked(service.listTasks).mockResolvedValue([]); page('/dashboard','USER');
    expect(await screen.findByRole('heading',{name:'Assigned tasks'})).toBeTruthy();
    expect(screen.getAllByText('0')).toHaveLength(4);
  });
  it('USER starts own task without exposing create, reassign or cancel controls', async () => {
    page('/tasks','USER'); await screen.findByRole('button',{name:'Start Clean 101'});
    expect(screen.queryByRole('button',{name:'Create task'})).toBeNull();
    expect(screen.queryByRole('button',{name:'Edit Clean 101'})).toBeNull();
    expect(screen.queryByRole('button',{name:'Cancel Clean 101'})).toBeNull();
    expect(service.listRooms).not.toHaveBeenCalled(); expect(service.listUsers).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button',{name:'Start Clean 101'}));
    expect(service.taskStatus).toHaveBeenCalledWith(1,'IN_PROGRESS');
  });
  it('USER completes in-progress work', async () => {
    vi.mocked(service.listTasks).mockResolvedValue([{...task,status:'IN_PROGRESS'}]); page('/tasks','USER');
    await userEvent.click(await screen.findByRole('button',{name:'Complete Clean 101'}));
    expect(service.taskStatus).toHaveBeenCalledWith(1,'COMPLETED');
  });
  it.each(['ASSIGNED','IN_PROGRESS'])('USER has no execution control for another worker\'s %s task', async status => {
    vi.mocked(service.listTasks).mockResolvedValue([{...task,status: /** @type {import('../services/housekeepingService.js').Task['status']} */ (status),assignedUser:{id:2,name:'Other housekeeper'}}]);
    page('/tasks','USER'); await screen.findByRole('table');
    expect(screen.queryByRole('button',{name:'Start Clean 101'})).toBeNull();
    expect(screen.queryByRole('button',{name:'Complete Clean 101'})).toBeNull();
    expect(service.taskStatus).not.toHaveBeenCalled();
  });
  it.each(['/rooms','/team'])('USER cannot mount %s or request its data', path => {
    page(path,'USER'); expect(screen.getByRole('heading',{name:'Access denied'})).toBeTruthy();
    expect(service.listRooms).not.toHaveBeenCalled(); expect(service.listUsers).not.toHaveBeenCalled();
  });
});
describe('Task management', () => {
  it.each(['MANAGER','ADMIN'])('%s manages tasks without execution controls', async role => {
    vi.mocked(service.listTasks).mockResolvedValue([task,{...task,id:2,title:'Clean 102',status:'IN_PROGRESS'}]);
    page('/tasks', /** @type {import('../routes/routeDefinitions.js').Role} */ (role)); await screen.findByRole('table');
    expect(screen.getByRole('button',{name:'Create task'})).toBeTruthy();
    expect(screen.getByRole('button',{name:'Edit Clean 101'})).toBeTruthy();
    expect(screen.getByRole('button',{name:'Cancel Clean 102'})).toBeTruthy();
    expect(screen.queryByRole('button',{name:'Start Clean 101'})).toBeNull();
    expect(screen.queryByRole('button',{name:'Complete Clean 102'})).toBeNull();
    expect(service.taskStatus).not.toHaveBeenCalled();
  });
  it('creates an assigned task with numeric ids, priority and optional local due time', async () => {
    page('/tasks'); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button',{name:'Create task'}));
    const dialog = screen.getByRole('dialog',{name:'Create task'});
    await userEvent.selectOptions(within(dialog).getByLabelText('Room (required)'),'1');
    await userEvent.selectOptions(within(dialog).getByLabelText('Housekeeper (required)'),'1');
    await userEvent.selectOptions(within(dialog).getByLabelText('Priority (required)'),'URGENT');
    await userEvent.click(within(dialog).getByRole('button',{name:'Save task'}));
    expect(service.save).toHaveBeenCalledWith('tasks',null,expect.objectContaining({roomId:1,assignedUserId:1,priority:'URGENT',dueAt:null}));
  });
  it('edits/reassigns without allowing room changes', async () => {
    page('/tasks'); await userEvent.click(await screen.findByRole('button',{name:'Edit Clean 101'}));
    expect(screen.getByLabelText('Room (required)')).toHaveProperty('disabled',true);
    await userEvent.clear(screen.getByLabelText('Title (required)')); await userEvent.type(screen.getByLabelText('Title (required)'),'Recheck clean');
    await userEvent.click(screen.getByRole('button',{name:'Save task'}));
    expect(service.save).toHaveBeenCalledWith('tasks',1,expect.objectContaining({title:'Recheck clean',roomId:1}));
  });
  it('supervisor cancels without hard-delete wording', async () => {
    page('/tasks'); await userEvent.click(await screen.findByRole('button',{name:'Cancel Clean 101'}));
    expect(service.deactivate).toHaveBeenCalledWith('tasks',1);
  });
  it('filters tasks by canonical status and preserves overdue evidence', async () => {
    page('/tasks','USER'); await screen.findByRole('table');
    expect(screen.getByText(/OVERDUE/)).toBeTruthy();
    await userEvent.selectOptions(screen.getByLabelText('Task status filter'),'COMPLETED');
    expect(screen.getByText('No tasks in this view.')).toBeTruthy();
  });
  it('shows safe failed-write recovery without closing the dialog', async () => {
    vi.mocked(service.save).mockRejectedValue(new ApiError(409)); page('/tasks'); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button',{name:'Edit Clean 101'}));
    await userEvent.click(screen.getByRole('button',{name:'Save task'}));
    expect(await screen.findByRole('alert')).toBeTruthy(); expect(screen.getByRole('dialog')).toBeTruthy();
  });
});
describe('Rooms and minimal inspection', () => {
  it('creates a room with a workflow-valid initial status', async () => {
    page('/rooms'); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button',{name:'Create room'}));
    await userEvent.type(screen.getByLabelText('Room number (required)'),'102');
    await userEvent.selectOptions(screen.getByLabelText('Initial status'),'DIRTY');
    await userEvent.click(screen.getByRole('button',{name:'Save room'}));
    expect(service.save).toHaveBeenCalledWith('rooms',null,{roomNumber:'102',floor:1,status:'DIRTY',active:true});
  });
  it.each(['PASS','FAIL'])('records %s against the latest completed cleaning task', async result => {
    vi.mocked(service.listRooms).mockResolvedValue([{...room,status:'INSPECTION'}]);
    vi.mocked(service.listTasks).mockResolvedValue([{...task,status:'COMPLETED',completedAt:time}]);
    page('/rooms'); await userEvent.click(await screen.findByRole('button',{name:'Inspect room 101'}));
    await userEvent.selectOptions(screen.getByLabelText('Result'),result);
    await userEvent.type(screen.getByLabelText('Inspection notes'),'Checked room');
    await userEvent.click(screen.getByRole('button',{name:'Record inspection'}));
    expect(service.inspect).toHaveBeenCalledWith(1,1,result,'Checked room');
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });
  it('reads retained inspection history', async () => {
    vi.mocked(service.listInspections).mockResolvedValue([{id:1,roomId:1,taskId:1,inspectedBy:{id:2,name:'Supervisor'},result:'FAIL',notes:'Needs rework',inspectedAt:time}]);
    page('/rooms'); await userEvent.click(await screen.findByRole('button',{name:'History room 101'}));
    expect(await screen.findByText('Needs rework')).toBeTruthy(); expect(service.listInspections).toHaveBeenCalledWith(1,expect.any(AbortSignal));
    expect(screen.queryByRole('button',{name:'Record inspection'})).toBeNull();
  });
  it('marks ready rooms dirty and preserves the inspection-only READY transition', async () => {
    vi.mocked(service.listRooms).mockResolvedValue([{...room,status:'READY'}]); page('/rooms');
    await userEvent.click(await screen.findByRole('button',{name:'Mark room 101 dirty'}));
    expect(service.roomStatus).toHaveBeenCalledWith(1,'DIRTY'); expect(screen.queryByRole('button',{name:/Mark.*ready/i})).toBeNull();
  });
  it('room edit preserves its number and sends floor/activity state', async () => {
    page('/rooms'); await userEvent.click(await screen.findByRole('button',{name:'Edit room 101'}));
    expect(screen.getByLabelText('Room number (required)')).toHaveProperty('disabled',true);
    await userEvent.click(screen.getByRole('button',{name:'Save room'}));
    expect(service.save).toHaveBeenCalledWith('rooms',1,expect.objectContaining({roomNumber:'101',floor:1,active:true}));
  });
});
describe('Team permissions and password hygiene', () => {
  it('Manager creates USER only with no privileged role control', async () => {
    page('/team'); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button',{name:'Create housekeeper'}));
    expect(screen.queryByLabelText('Role (required)')).toBeNull();
    await userEvent.type(screen.getByLabelText('Name (required)'),'New worker');
    await userEvent.type(screen.getByLabelText('Email (required)'),'new@example.test');
    const password = crypto.randomUUID();
    await userEvent.type(screen.getByLabelText('Initial password (required)'),password);
    await userEvent.click(screen.getByRole('button',{name:'Save team member'}));
    expect(service.save).toHaveBeenCalledWith('users',null,{name:'New worker',email:'new@example.test',password,role:'USER'});
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.queryByDisplayValue(password)).toBeNull();
  });
  it('Admin can create Manager but no ADMIN option exists', async () => {
    page('/team','ADMIN'); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button',{name:'Create housekeeper or manager'}));
    expect(screen.queryByRole('option',{name:'Admin'})).toBeNull();
    await userEvent.selectOptions(screen.getByLabelText('Role (required)'),'MANAGER');
    expect(screen.getByLabelText('Role (required)')).toHaveProperty('value','MANAGER');
  });
  it('Admin edit/deactivate controls never appear for bootstrap Admin', async () => {
    vi.mocked(service.listUsers).mockResolvedValue([testUser('ADMIN')]); page('/team','ADMIN'); await screen.findByRole('table');
    expect(screen.queryByRole('button',{name:'Edit Synthetic reviewer'})).toBeNull();
    expect(screen.queryByRole('button',{name:'Deactivate Synthetic reviewer'})).toBeNull();
  });
  it('Admin updates a worker without sending a password', async () => {
    page('/team','ADMIN'); await userEvent.click(await screen.findByRole('button',{name:'Edit Synthetic housekeeper'}));
    expect(screen.queryByLabelText('Initial password (required)')).toBeNull();
    await userEvent.click(screen.getByRole('button',{name:'Save team member'}));
    expect(service.save).toHaveBeenCalledWith('users',1,{name:'Synthetic housekeeper',email:'reviewer@example.test',role:'USER',active:true});
  });
});
describe('Loading, errors, refresh and focus', () => {
  it('shows loading then empty results', async () => {
    const pending = deferred(); vi.mocked(service.listTasks).mockReturnValue(pending.promise); page('/tasks','USER');
    expect(screen.getByRole('status').textContent).toContain('Loading tasks');
    await act(async () => { pending.resolve([]); }); expect(screen.getByText('No tasks in this view.')).toBeTruthy();
  });
  it.each([403,429,500,0])('read error %s is recoverable without exposing previous data', async code => {
    vi.mocked(service.listTasks).mockRejectedValueOnce(new ApiError(code)); page('/tasks','USER');
    expect(await screen.findByRole('alert')).toBeTruthy(); expect(screen.queryByText('Clean 101')).toBeNull();
    await userEvent.click(screen.getByRole('button',{name:'Retry'})); expect(await screen.findByText('Clean 101')).toBeTruthy();
  });
  it('keeps loaded content and opener mounted during background reload, then restores focus', async () => {
    page('/rooms'); const opener = await screen.findByRole('button',{name:'Edit room 101'});
    await userEvent.click(opener);
    const pending = deferred(); vi.mocked(service.listRooms).mockReturnValueOnce(pending.promise);
    await userEvent.click(screen.getByRole('button',{name:'Save room'}));
    expect(await screen.findByText('Refreshing rooms…')).toBeTruthy();
    expect(document.activeElement).toBe(opener); expect(screen.getByRole('table')).toBeTruthy();
    await act(async () => { pending.resolve([room]); });
  });
  it('ignores a late request after identity/logout change', async () => {
    const pending = deferred(); vi.mocked(service.listTasks).mockReturnValueOnce(pending.promise);
    const view = page('/tasks','USER');
    view.rerender(<MemoryRouter><TestAuth><App /></TestAuth></MemoryRouter>);
    await act(async () => { pending.resolve([task]); });
    expect(screen.queryByText('Clean 101')).toBeNull(); expect(screen.getByRole('heading',{name:'Sign in',level:1})).toBeTruthy();
  });
  it('refuses native dialog close during a pending save', async () => {
    page('/rooms'); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button',{name:'Edit room 101'}));
    const pending = deferred(); vi.mocked(service.save).mockReturnValueOnce(pending.promise);
    await userEvent.click(screen.getByRole('button',{name:'Save room'}));
    const dialog = /** @type {HTMLDialogElement} */ (screen.getByRole('dialog')); dialog.close(); fireEvent(dialog,new Event('close'));
    expect(dialog.open).toBe(true); fireEvent(dialog,new Event('cancel',{cancelable:true})); expect(dialog.open).toBe(true);
    await act(async () => { pending.resolve({}); }); expect(screen.queryByRole('dialog')).toBeNull();
  });
});

it('keeps a logical focus fallback when inspection success removes the opener after background refresh', async () => {
  vi.mocked(service.listRooms).mockResolvedValueOnce([{...room,status:'INSPECTION'}]);
  vi.mocked(service.listTasks).mockResolvedValue([{...task,status:'COMPLETED',completedAt:time}]);
  page('/rooms');
  const opener = await screen.findByRole('button',{name:'Inspect room 101'});
  await userEvent.click(opener);
  const pending = deferred(); vi.mocked(service.listRooms).mockReturnValueOnce(pending.promise);
  await userEvent.click(screen.getByRole('button',{name:'Record inspection'}));
  expect(await screen.findByText('Refreshing rooms…')).toBeTruthy();
  expect(document.activeElement).toBe(opener);
  await act(async () => { pending.resolve([{...room,status:'READY'}]); });
  await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('heading',{name:'Rooms',level:1})));
});
