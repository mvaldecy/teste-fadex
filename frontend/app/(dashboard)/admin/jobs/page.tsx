import { AdminRouteGuard } from "@/src/components/layout/admin-route-guard";
import { AiJobsPage } from "@/src/features/ai-jobs/ai-jobs-page";

export default function AiJobsRoutePage() {
  return (
    <AdminRouteGuard>
      <AiJobsPage />
    </AdminRouteGuard>
  );
}
