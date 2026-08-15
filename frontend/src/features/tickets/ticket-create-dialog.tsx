"use client";

import { FilePlus2 } from "lucide-react";
import { useState } from "react";
import { Button } from "@/src/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/src/components/ui/dialog";
import { Input } from "@/src/components/ui/input";
import { Label } from "@/src/components/ui/label";
import { Textarea } from "@/src/components/ui/textarea";
import {
  createTicketSchema,
  type CreateTicketData
} from "@/src/schemas/ticket.schema";

type FieldErrors = Partial<Record<keyof CreateTicketData, string>>;

type TicketCreateDialogProps = {
  isCreating: boolean;
  onCreateTicket: (payload: CreateTicketData) => Promise<boolean>;
};

const initialValues: CreateTicketData = {
  title: "",
  description: ""
};

export function TicketCreateDialog({
  isCreating,
  onCreateTicket
}: TicketCreateDialogProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [values, setValues] = useState<CreateTicketData>(initialValues);
  const [errors, setErrors] = useState<FieldErrors>({});

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsed = createTicketSchema.safeParse(values);

    if (!parsed.success) {
      const fieldErrors = parsed.error.flatten().fieldErrors;
      setErrors({
        title: fieldErrors.title?.[0],
        description: fieldErrors.description?.[0]
      });
      return;
    }

    setErrors({});

    const didCreate = await onCreateTicket(parsed.data);

    if (didCreate) {
      setValues(initialValues);
      setIsOpen(false);
    }
  }

  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogTrigger asChild>
        <Button>
          <FilePlus2 className="h-4 w-4" />
          Novo chamado
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form className="grid gap-5" onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Novo chamado</DialogTitle>
            <DialogDescription>
              Registre a solicitação para triagem da equipe.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-2">
            <Label htmlFor="ticket-title">Título</Label>
            <Input
              id="ticket-title"
              value={values.title}
              onChange={(event) =>
                setValues((currentValues) => ({
                  ...currentValues,
                  title: event.target.value
                }))
              }
            />
            {errors.title ? (
              <p className="text-sm font-medium text-red-700">
                {errors.title}
              </p>
            ) : null}
          </div>

          <div className="grid gap-2">
            <Label htmlFor="ticket-description">Descrição</Label>
            <Textarea
              id="ticket-description"
              value={values.description}
              onChange={(event) =>
                setValues((currentValues) => ({
                  ...currentValues,
                  description: event.target.value
                }))
              }
            />
            {errors.description ? (
              <p className="text-sm font-medium text-red-700">
                {errors.description}
              </p>
            ) : null}
          </div>

          <DialogFooter>
            <Button disabled={isCreating} type="submit">
              {isCreating ? "Salvando..." : "Criar chamado"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
