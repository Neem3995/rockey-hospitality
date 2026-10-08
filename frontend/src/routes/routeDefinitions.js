/** Backend USER is shown as Housekeeper; labels never grant permissions. */
/** @typedef {'USER' | 'MANAGER' | 'ADMIN'} Role */
/** @typedef {{path: string, title: string, access: 'public' | 'protected', roles: Role[]}} RouteDefinition */
/** @type {RouteDefinition[]} */
export const routeDefinitions = [
  { path: '/login', title: 'Sign in', access: 'public', roles: [] },
  { path: '/register', title: 'Create an account', access: 'public', roles: [] },
  { path: '/dashboard', title: 'Dashboard', access: 'protected', roles: ['USER', 'MANAGER', 'ADMIN'] },
  { path: '/tasks', title: 'Tasks', access: 'protected', roles: ['USER', 'MANAGER', 'ADMIN'] },
  { path: '/rooms', title: 'Rooms', access: 'protected', roles: ['MANAGER', 'ADMIN'] },
  { path: '/team', title: 'Team', access: 'protected', roles: ['MANAGER', 'ADMIN'] },
];
