import { Eye } from "lucide-react";
import Link from "next/link";
import { Button } from "@/src/components/ui/button";
import { routes } from "@/src/routes/routes";

type TicketActionsProps = {
  ticketId: string;
};

export function TicketActions({ ticketId }: TicketActionsProps) {
  return (
    <Button asChild size="sm" variant="outline">
      <Link href={routes.ticketDetails(ticketId)}>
        <Eye className="h-4 w-4" />
        Visualizar
      </Link>
    </Button>
  );
}
