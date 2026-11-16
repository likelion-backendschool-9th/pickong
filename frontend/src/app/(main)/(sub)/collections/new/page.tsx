"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useCreateCollection } from "@/entities/collection";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { CollectionForm, priceError, type CollectionFormValue } from "../_components/CollectionForm";

export default function NewCollectionPage() {
  const router = useRouter();
  const create = useCreateCollection();
  const [value, setValue] = useState<CollectionFormValue>({ title: "", description: "", visibility: "PRIVATE", pinColor: "LIME", price: null });

  return (
    <Screen>
      <TopBar title="새 컬렉션" backHref={routes.collections()} />
      <div className="px-5 pt-2 pb-6">
        <CollectionForm value={value} onChange={setValue} />
        {create.isError && <Notice tone="danger" className="mt-4">{errorMessage(create.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          disabled={!value.title.trim() || !!priceError(value.price)}
          loading={create.isPending}
          onClick={() => create.mutate(value, { onSuccess: (c) => router.replace(routes.collection(c.id)) })}
        >
          컬렉션 만들기
        </Button>
      </BottomCta>
    </Screen>
  );
}
