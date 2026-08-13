import { redirect } from "next/navigation";
import { routes } from "@/src/routes/routes";

export default function Page() {
  redirect(routes.login);
}
