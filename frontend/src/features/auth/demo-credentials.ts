/**
 * Contas semeadas, oferecidas na tela de login como atalho para quem avalia o
 * projeto.
 *
 * Existe so quando `NEXT_PUBLIC_DEMO_LOGIN` esta ligada, e o compose deriva
 * essa variavel de `APP_SEED_ENABLED`. O acoplamento e proposital: sem o seed
 * estas contas não existem no banco, e um atalho que preenche credenciais
 * invalidas e pior que atalho nenhum — manda o avaliador depurar um login que
 * nunca ia funcionar.
 */
export type DemoCredential = {
  label: string;
  role: "ADMIN" | "SOLICITANTE";
  description: string;
  email: string;
  password: string;
};

export const demoCredentials: DemoCredential[] = [
  {
    label: "Administrador",
    role: "ADMIN",
    description: "Vê todos os chamados, o painel e a fila de jobs",
    email: "admin@fadex.org.br",
    password: "admin123"
  },
  {
    label: "Carla Menezes",
    role: "ADMIN",
    description: "Segunda administradora, para testar atribuição entre pessoas",
    email: "carla.menezes@fadex.org.br",
    password: "admin123"
  },
  {
    label: "Marcos Valdecy",
    role: "ADMIN",
    description: "Conta de desenvolvimento",
    email: "mvaldecy11@gmail.com",
    password: "dev123"
  },
  {
    label: "Solicitante",
    role: "SOLICITANTE",
    description: "Vê apenas os chamados que abriu",
    email: "solicitante@fadex.org.br",
    password: "solicitante123"
  },
  {
    label: "Ana Ribeiro",
    role: "SOLICITANTE",
    description: "Abriu chamados de infraestrutura e equipamentos",
    email: "ana.ribeiro@fadex.org.br",
    password: "solicitante123"
  },
  {
    label: "Bruno Carvalho",
    role: "SOLICITANTE",
    description: "Abriu chamados de acesso e financeiro",
    email: "bruno.carvalho@fadex.org.br",
    password: "solicitante123"
  }
];
