import { redirect } from "next/navigation";
import { routes } from "@/src/routes/routes";

export default function HomePage() {
  redirect(routes.dashboard);
}
