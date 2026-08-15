"use client";

import { Ban } from "lucide-react";
import { useState } from "react";
import { Button } from "@/src/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/src/components/ui/dialog";

type TicketCancelActionProps = {
  isSubmitting: boolean;
  onCancel: () => Promise<boolean>;
};

/**
 * Cancelar tem cartao próprio, fora do seletor de status.
 *
 * Duas razoes: e irreversivel — chamado cancelado não reabre — e e a única ação
 * de escrita que o SOLICITANTE tem sobre o próprio chamado, entao aparece
 * também para quem não ve o restante das ações de ciclo de vida.
 *
 * Quem decide se este cartao aparece e a página de detalhe, cruzando a matriz
 * do servidor com o papel. O servidor reconfere de todo jeito.
 */
export function TicketCancelAction({
  isSubmitting,
  onCancel
}: TicketCancelActionProps) {
  const [isOpen, setIsOpen] = useState(false);

  async function handleConfirm() {
    const cancelled = await onCancel();

    if (cancelled) {
      setIsOpen(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Cancelar chamado</CardTitle>
        <CardDescription>
          O chamado sai do fluxo de atendimento e não reabre. O histórico e os
          comentários continuam disponiveis para consulta.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <Dialog open={isOpen} onOpenChange={setIsOpen}>
          <DialogTrigger asChild>
            <Button disabled={isSubmitting} type="button" variant="destructive">
              <Ban className="h-4 w-4" />
              Cancelar chamado
            </Button>
          </DialogTrigger>

          <DialogContent>
            <DialogHeader>
              <DialogTitle>Cancelar este chamado?</DialogTitle>
              <DialogDescription>
                A ação não pode ser desfeita. Chamado cancelado não reabre — se
                voltar a precisar de atendimento, abra um chamado novo.
              </DialogDescription>
            </DialogHeader>

            <DialogFooter>
              <Button
                disabled={isSubmitting}
                type="button"
                variant="outline"
                onClick={() => setIsOpen(false)}
              >
                Manter chamado
              </Button>
              <Button
                disabled={isSubmitting}
                type="button"
                variant="destructive"
                onClick={handleConfirm}
              >
                Cancelar chamado
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardContent>
    </Card>
  );
}
