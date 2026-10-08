import { apiRequest } from './apiClient.js';

/** @typedef {import('../routes/routeDefinitions.js').Role} Role */
/** @typedef {{id:number,name:string,email:string,role:Role,active:boolean}} TeamUser */
/** @typedef {'READY'|'DIRTY'|'CLEANING'|'INSPECTION'|'OUT_OF_SERVICE'} RoomStatus */
/** @typedef {{id:number,roomNumber:string,floor:number,status:RoomStatus,active:boolean,createdAt:string,updatedAt:string}} Room */
/** @typedef {'ASSIGNED'|'IN_PROGRESS'|'COMPLETED'|'CANCELLED'} TaskStatus */
/** @typedef {'LOW'|'MEDIUM'|'HIGH'|'URGENT'} Priority */
/** @typedef {{id:number,title:string,description:string|null,status:TaskStatus,priority:Priority,assignedUser:{id:number,name:string},room:Room,dueAt:string|null,completedAt:string|null,createdAt:string,updatedAt:string}} Task */
/** @typedef {{id:number,roomId:number,taskId:number,inspectedBy:{id:number,name:string},result:'PASS'|'FAIL',notes:string|null,inspectedAt:string}} Inspection */
/** @param {string} path @param {AbortSignal} [signal] @returns {Promise<unknown>} */
function read(path, signal) { return apiRequest(path, { signal }); }
/** @param {string} path @param {'POST'|'PUT'|'DELETE'} method @param {unknown} [body] */
function write(path, method, body) {
  return apiRequest(path, { method, headers: { 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
}
/** @param {AbortSignal} [signal] @returns {Promise<Task[]>} */
export async function listTasks(signal) { return /** @type {Task[]} */ (await read('/tasks', signal)); }
/** @param {AbortSignal} [signal] @returns {Promise<Room[]>} */
export async function listRooms(signal) { return /** @type {Room[]} */ (await read('/rooms', signal)); }
/** @param {AbortSignal} [signal] @returns {Promise<TeamUser[]>} */
export async function listUsers(signal) { return /** @type {TeamUser[]} */ (await read('/users', signal)); }
/** @param {number} id @param {AbortSignal} [signal] @returns {Promise<Inspection[]>} */
export async function listInspections(id, signal) { return /** @type {Inspection[]} */ (await read('/rooms/' + id + '/inspections', signal)); }
/** @param {'tasks'|'rooms'|'users'} domain @param {number|null} id @param {unknown} body */
export function save(domain, id, body) { return write('/' + domain + (id === null ? '' : '/' + id), id === null ? 'POST' : 'PUT', body); }
/** @param {'tasks'|'rooms'|'users'} domain @param {number} id */
export function deactivate(domain, id) { return write('/' + domain + '/' + id, 'DELETE'); }
/** @param {number} id @param {TaskStatus} status */
export function taskStatus(id, status) { return write('/tasks/' + id + '/status', 'PUT', { status }); }
/** @param {number} id @param {RoomStatus} status */
export function roomStatus(id, status) { return write('/rooms/' + id + '/status', 'PUT', { status }); }
/** @param {number} roomId @param {number} taskId @param {'PASS'|'FAIL'} result @param {string} notes */
export function inspect(roomId, taskId, result, notes) { return write('/rooms/' + roomId + '/inspections', 'POST', { taskId, result, notes }); }
/** @param {Role} role */
export function roleLabel(role) { return role === 'USER' ? 'Housekeeper' : role === 'MANAGER' ? 'Manager' : 'Admin'; }
/** Wall-clock ISO date/time in the approved hotel zone; naive API timestamps are not silently relabeled UTC. */
export function hotelNow() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/New_York', year: 'numeric', month: '2-digit',
    day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).formatToParts(new Date());
  const part = (/** @type {string} */ key) => parts.find(p => p.type === key)?.value || '';
  return part('year') + '-' + part('month') + '-' + part('day') + 'T' + part('hour') + ':' + part('minute') + ':' + part('second');
}
/** @param {Task} task @param {string} [now] */
export function overdue(task, now = hotelNow()) {
  return !!task.dueAt && task.dueAt < now && !['COMPLETED', 'CANCELLED'].includes(task.status);
}
/** @param {string|null} time */
export function displayTime(time) { return time ? time.replace('T', ' ').slice(0, 16) + ' (hotel local time)' : 'Not set'; }
