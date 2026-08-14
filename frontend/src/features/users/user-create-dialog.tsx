"use client";

import { UserPlus } from "lucide-react";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/src/components/ui/select";
import {
  createUserFormSchema,
  type CreateUserFormData
} from "@/src/schemas/user.schema";
import type { ChoicesResponse } from "@/src/types/api";

type FieldErrors = Partial<Record<keyof CreateUserFormData, string>>;

type UserCreateDialogProps = {
  choices: ChoicesResponse | null;
  isCreating: boolean;
  onCreateUser: (payload: CreateUserFormData) => Promise<boolean>;
};

const initialValues = {
  name: "",
  email: "",
  role: ""
};

export function UserCreateDialog({
  choices,
  isCreating,
  onCreateUser
}: UserCreateDialogProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState<FieldErrors>({});

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsed = createUserFormSchema.safeParse(values);

    if (!parsed.success) {
      const fieldErrors = parsed.error.flatten().fieldErrors;
      setErrors({
        name: fieldErrors.name?.[0],
        email: fieldErrors.email?.[0],
        role: fieldErrors.role?.[0]
      });
      return;
    }

    setErrors({});

    const didCreate = await onCreateUser(parsed.data);

    if (didCreate) {
      setValues(initialValues);
      setIsOpen(false);
    }
  }

  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogTrigger asChild>
        <Button>
          <UserPlus className="h-4 w-4" />
          Novo usuario
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form className="grid gap-5" onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Novo usuario</DialogTitle>
            <DialogDescription>
              A senha provisoria e gerada pelo backend e enviada por e-mail.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-2">
            <Label htmlFor="user-name">Nome</Label>
            <Input
              id="user-name"
              value={values.name}
              onChange={(event) =>
                setValues((current) => ({ ...current, name: event.target.value }))
              }
            />
            {errors.name ? (
              <p className="text-sm font-medium text-red-700">{errors.name}</p>
            ) : null}
          </div>

          <div className="grid gap-2">
            <Label htmlFor="user-email">E-mail</Label>
            <Input
              id="user-email"
              type="email"
              value={values.email}
              onChange={(event) =>
                setValues((current) => ({
                  ...current,
                  email: event.target.value
                }))
              }
            />
            {errors.email ? (
              <p className="text-sm font-medium text-red-700">{errors.email}</p>
            ) : null}
          </div>

          <div className="grid gap-2">
            <Label htmlFor="user-role">Perfil</Label>
            <Select
              value={values.role}
              onValueChange={(role) =>
                setValues((current) => ({ ...current, role }))
              }
            >
              <SelectTrigger id="user-role">
                <SelectValue placeholder="Selecione o perfil" />
              </SelectTrigger>
              <SelectContent>
                {choices?.roles.map((choice) => (
                  <SelectItem key={choice.value} value={choice.value}>
                    {choice.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.role ? (
              <p className="text-sm font-medium text-red-700">{errors.role}</p>
            ) : null}
          </div>

          <DialogFooter>
            <Button disabled={isCreating} type="submit">
              {isCreating ? "Criando..." : "Criar usuario"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
