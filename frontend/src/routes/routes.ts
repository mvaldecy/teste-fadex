export const routes = {
  login: "/login",
  home: "/home",
  dashboard: "/dashboard",
  tickets: "/tickets",
  ticketDetails: (ticketId: string) => `/tickets/${ticketId}`,
  users: "/usuarios",
  adminJobs: "/admin/jobs"
} as const;
