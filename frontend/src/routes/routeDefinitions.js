/** @typedef {'USER' | 'STAFF' | 'ADMIN'} Role */
/**
 * @typedef {object} RouteDefinition
 * @property {string} path
 * @property {string} title
 * @property {'public' | 'protected'} access
 * @property {Role[]} roles Client route eligibility; backend authorization remains authoritative.
 */

/** @type {RouteDefinition[]} */
export const routeDefinitions = [
  { path: '/login', title: 'Sign in', access: 'public', roles: [] },
  { path: '/register', title: 'Create an account', access: 'public', roles: [] },
  { path: '/dashboard', title: 'Dashboard', access: 'protected', roles: ['USER', 'STAFF', 'ADMIN'] },
  { path: '/events', title: 'Events', access: 'protected', roles: ['USER', 'STAFF', 'ADMIN'] },
  { path: '/events/:eventId', title: 'Event details', access: 'protected', roles: ['USER', 'STAFF', 'ADMIN'] },
  { path: '/registrations', title: 'My registrations', access: 'protected', roles: ['USER'] },
  { path: '/rooms', title: 'Rooms', access: 'protected', roles: ['STAFF', 'ADMIN'] },
  { path: '/rooms/:roomId', title: 'Room details', access: 'protected', roles: ['STAFF', 'ADMIN'] },
  { path: '/tasks', title: 'Tasks', access: 'protected', roles: ['STAFF', 'ADMIN'] },
  { path: '/tasks/:taskId', title: 'Task details', access: 'protected', roles: ['STAFF', 'ADMIN'] },
  { path: '/inventory', title: 'Inventory', access: 'protected', roles: ['STAFF', 'ADMIN'] },
  { path: '/inventory/:itemId', title: 'Inventory item', access: 'protected', roles: ['STAFF', 'ADMIN'] },
  { path: '/alerts', title: 'Alerts', access: 'protected', roles: ['STAFF', 'ADMIN'] },
  { path: '/alerts/:alertId', title: 'Alert details', access: 'protected', roles: ['STAFF', 'ADMIN'] },
  { path: '/analytics', title: 'Analytics', access: 'protected', roles: ['ADMIN'] },
  { path: '/admin/employees', title: 'Employees', access: 'protected', roles: ['ADMIN'] },
  { path: '/admin/departments', title: 'Departments', access: 'protected', roles: ['ADMIN'] },
];
