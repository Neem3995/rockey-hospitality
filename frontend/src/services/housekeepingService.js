import { apiRequest, ApiError } from './apiClient.js';

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
/** These guards cover fields the UI consumes, not server-side business validation.
 * @param {unknown} value @returns {value is Record<string, unknown>}
 */
function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
/** @param {unknown} value */
function id(value) { return typeof value === 'number' && Number.isSafeInteger(value) && value > 0; }
/** @param {unknown} value */
function optionalText(value) { return value == null || typeof value === 'string'; }
/** @param {unknown} value */
function person(value) { return object(value) && id(value.id) && typeof value.name === 'string'; }
/** @param {unknown} value */
function roomShape(value) {
  return object(value) && id(value.id) && typeof value.roomNumber === 'string'
    && typeof value.floor === 'number' && Number.isInteger(value.floor) && typeof value.active === 'boolean'
    && typeof value.status === 'string' && ['READY', 'DIRTY', 'CLEANING', 'INSPECTION', 'OUT_OF_SERVICE'].includes(value.status);
}
/** @param {unknown} value */
function taskShape(value) {
  return object(value) && id(value.id) && typeof value.title === 'string' && optionalText(value.description)
    && typeof value.status === 'string' && ['ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'].includes(value.status)
    && typeof value.priority === 'string' && ['LOW', 'MEDIUM', 'HIGH', 'URGENT'].includes(value.priority)
    && person(value.assignedUser) && roomShape(value.room) && optionalText(value.dueAt) && optionalText(value.completedAt);
}
/** @param {unknown} value */
function userShape(value) {
  return person(value) && object(value) && typeof value.email === 'string' && typeof value.active === 'boolean'
    && typeof value.role === 'string' && ['USER', 'MANAGER', 'ADMIN'].includes(value.role);
}
/** @param {unknown} value */
function inspectionShape(value) {
  return object(value) && id(value.id) && id(value.roomId) && id(value.taskId) && person(value.inspectedBy)
    && typeof value.result === 'string' && ['PASS', 'FAIL'].includes(value.result)
    && optionalText(value.notes) && typeof value.inspectedAt === 'string';
}
/** @param {unknown} value @param {(row: unknown) => boolean} valid @returns {unknown[]} */
function checkedList(value, valid) {
  if (!Array.isArray(value) || !value.every(valid)) throw new ApiError(502);
  return value;
}
/** @param {AbortSignal} [signal] @returns {Promise<Task[]>} */
export async function listTasks(signal) { return /** @type {Task[]} */ (checkedList(await read('/tasks', signal), taskShape)); }
/** @param {AbortSignal} [signal] @returns {Promise<Room[]>} */
export async function listRooms(signal) { return /** @type {Room[]} */ (checkedList(await read('/rooms', signal), roomShape)); }
/** @param {AbortSignal} [signal] @returns {Promise<TeamUser[]>} */
export async function listUsers(signal) { return /** @type {TeamUser[]} */ (checkedList(await read('/users', signal), userShape)); }
/** @param {number} id @param {AbortSignal} [signal] @returns {Promise<Inspection[]>} */
export async function listInspections(id, signal) { return /** @type {Inspection[]} */ (checkedList(await read('/rooms/' + id + '/inspections', signal), inspectionShape)); }
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
