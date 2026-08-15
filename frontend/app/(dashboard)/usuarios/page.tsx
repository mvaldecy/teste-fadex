import { AdminRouteGuard } from "@/src/components/layout/admin-route-guard";
import { UsersPage } from "@/src/features/users/users-page";

export default function UsersRoutePage() {
  return (
    <AdminRouteGuard>
      <UsersPage />
    </AdminRouteGuard>
  );
}
