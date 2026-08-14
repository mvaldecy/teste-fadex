export const routes = {
  login: "/login",
  home: "/home",
  tickets: "/tickets",
  ticketDetails: (ticketId: string) => `/tickets/${ticketId}`
} as const;
