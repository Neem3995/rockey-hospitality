/*
 * STUDY NOTE: AppLayout reads this shared route metadata for titles and role-filtered navigation.
 * Each object holds a path, label and role list; USER can display as Housekeeper without renaming it.
 * This data does not register App's routes or grant API permission. There is no fetch or mutable session here.
 */
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
