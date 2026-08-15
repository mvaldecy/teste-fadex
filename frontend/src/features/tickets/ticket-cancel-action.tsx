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
 * Cancelar tem cartao proprio, fora do seletor de status.
 *
 * Duas razoes: e irreversivel — chamado cancelado nao reabre — e e a unica acao
 * de escrita que o SOLICITANTE tem sobre o proprio chamado, entao aparece
 * tambem para quem nao ve o restante das acoes de ciclo de vida.
 *
 * Quem decide se este cartao aparece e a pagina de detalhe, cruzando a matriz
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
          O chamado sai do fluxo de atendimento e nao reabre. O historico e os
          comentarios continuam disponiveis para consulta.
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
                A acao nao pode ser desfeita. Chamado cancelado nao reabre — se
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
