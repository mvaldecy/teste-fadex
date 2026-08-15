import { AdminRouteGuard } from "@/src/components/layout/admin-route-guard";
import { DashboardPage } from "@/src/features/indicators/dashboard-page";

export default function DashboardRoutePage() {
  return (
    <AdminRouteGuard>
      <DashboardPage />
    </AdminRouteGuard>
  );
}
