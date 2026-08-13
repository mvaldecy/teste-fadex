let apiAccessToken: string | null = null;

export function getApiAccessToken() {
  return apiAccessToken;
}

export function setApiAccessToken(token: string | null) {
  apiAccessToken = token;
}
